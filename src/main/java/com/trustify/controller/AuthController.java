package com.trustify.controller;

import com.trustify.dto.AuthResponse;
import com.trustify.dto.LoginRequest;
import com.trustify.dto.SignupRequest;
import com.trustify.model.BlacklistedToken;
import com.trustify.model.User;
import com.trustify.repository.TokenBlacklistRepository;
import com.trustify.repository.UserRepository;
import com.trustify.security.JwtUtil;
import com.trustify.service.CustomUserDetailsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    PasswordEncoder passwordEncoder;


    @Autowired
    TokenBlacklistRepository tokenBlacklistRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ Signup
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Email already registered"));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.USER);

        userRepository.save(user);
        String token = jwtUtil.generateToken(request.getEmail());

        return ResponseEntity.ok(new AuthResponse(request.getUsername(), request.getEmail(), token));
    }

    // ✅ Login
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtUtil.generateToken(user.getEmail());
            return ResponseEntity.ok(new AuthResponse(user.getUsername(), user.getEmail(), token));

        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "Invalid email or password"));
        }
    }

    // logout
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {

        if(authHeader == null || !authHeader.startsWith("Bearer "))
        {
            return ResponseEntity.badRequest().body("Invalid Authorization Header");
        }
        String token = authHeader.substring(7);
        LocalDateTime expiry = jwtUtil.extractExpiration(token)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        BlacklistedToken blacklisted = new BlacklistedToken();
        blacklisted.setToken(token);
        blacklisted.setExpiresAt(expiry);

        tokenBlacklistRepository.save(blacklisted);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ✅ Token Validation (for Postman testing)
    @GetMapping("/validate-test")
    public ResponseEntity<?> validateTokenParam(@RequestParam String token) {
        String username = jwtUtil.extractUsername(token);
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        boolean isValid = jwtUtil.validateToken(token, userDetails);
        return ResponseEntity.ok(Collections.singletonMap("valid", isValid));
    }

    @GetMapping("/validate")
    public Map<String, Object> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        var userDetails = customUserDetailsService.loadUserByUsername(username);

        boolean isValid = jwtUtil.validateToken(token, userDetails);

        if (!isValid) {
            throw new RuntimeException("Invalid or expired token");
        }

        return Map.of(
                "valid", true,
                "username", username
        );
    }
}
