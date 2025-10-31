package com.trustify.controller;

import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import com.trustify.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {

    private static final Logger log = (Logger) LoggerFactory.getLogger(GoogleAuthController.class);

    @Value("${GOOGLE_CLIENT_ID}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public GoogleAuthController(RestTemplate restTemplate,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                JwtUtil jwtUtil) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/callback")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam String code,
                                                  @RequestParam(required = false) String redirect_uri) {
        try {
            // 1) Exchange code for tokens
            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);

            // Use the same redirect_uri that Google sent the code to; fallback to common playground if provided
            if (redirect_uri != null && !redirect_uri.isEmpty()) {
                params.add("redirect_uri", redirect_uri);
            } else {
                // if you tested via OAuth Playground, use playground redirect
                params.add("redirect_uri", "https://developers.google.com/oauthplayground");
            }

            params.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                log.error("Token endpoint returned non-2xx: {}", tokenResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String idToken = (String) tokenResponse.getBody().get("id_token");
            if (idToken == null) {
                log.error("ID token missing in tokenResponse: {}", tokenResponse.getBody());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // 2) Validate/inspect id_token via Google's tokeninfo endpoint
            String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> userInfoResponse = restTemplate.getForEntity(tokenInfoUrl, Map.class);
            if (!userInfoResponse.getStatusCode().is2xxSuccessful() || userInfoResponse.getBody() == null) {
                log.error("tokeninfo failed: {}", userInfoResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> userInfo = userInfoResponse.getBody();
            String email = (String) userInfo.get("email");
            String name = (String) userInfo.getOrDefault("name", email);

            if (email == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email not provided by Google"));
            }

            // 3) Create user if not exists
            Optional<User> maybeUser = userRepository.findByEmail(email);
            User user;
            if (maybeUser.isPresent()) {
                user = maybeUser.get();
            } else {
                user = new User();
                user.setUsername(email); // or parse nicer username from name
                user.setEmail(email);
                // create random password (never used) but store hashed
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                user.setPhone(null);
                // set role, status defaults etc. (adapt to your model)
                user.setRole(User.Role.USER); // if your Role is enum; adapt accordingly
                user.setStatus(User.AccountStatus.ACTIVE);
                userRepository.save(user);
            }

            // 4) Generate your JWT and return
            String jwtToken = jwtUtil.generateToken(user.getUsername());
            return ResponseEntity.ok(Map.of("token", jwtToken, "email", user.getEmail(), "username", user.getUsername()));

        } catch (Exception ex) {
            log.error("HandleGoogleCallback exception", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Google auth failed"));
        }
    }

}
