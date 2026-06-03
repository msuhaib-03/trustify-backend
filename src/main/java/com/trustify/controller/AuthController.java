package com.trustify.controller;

import com.trustify.dto.AuthResponse;
import com.trustify.dto.LoginRequest;
import com.trustify.dto.SignupRequest;
import com.trustify.model.BlacklistedToken;
import com.trustify.model.PasswordResetToken;
import com.trustify.model.User;
import com.trustify.repository.PasswordResetTokenRepository;
import com.trustify.repository.TokenBlacklistRepository;
import com.trustify.repository.UserRepository;
import com.trustify.security.JwtUtil;
import com.trustify.service.CustomUserDetailsService;
import com.trustify.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

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

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;


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

        return ResponseEntity.ok(new AuthResponse(user.getId(), request.getUsername(), request.getEmail(), token, user.getRole()));
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
            return ResponseEntity.ok(new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), token, user.getRole()));

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

    // ✅ Forgot Password — sends reset link to email
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        // Always return 200 to prevent email enumeration attacks
        String genericMessage = "If that email is registered you'll receive a reset link shortly. Check your spam folder too.";

        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            // Remove any existing token for this email
            passwordResetTokenRepository.deleteByEmail(email.trim().toLowerCase());

            String resetToken = UUID.randomUUID().toString();
            PasswordResetToken tokenDoc = PasswordResetToken.builder()
                    .token(resetToken)
                    .email(email.trim().toLowerCase())
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();
            passwordResetTokenRepository.save(tokenDoc);

            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            emailService.sendPasswordResetEmail(email.trim().toLowerCase(), resetLink);
        });

        return ResponseEntity.ok(Map.of("message", genericMessage));
    }

    // ✅ Reset Password — validates token and updates password
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset token is required"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElse(null);

        if (resetToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset link. Please request a new one."));
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken);
            return ResponseEntity.badRequest().body(Map.of("error", "This reset link has expired. Please request a new one."));
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElse(null);

        if (user == null) {
            passwordResetTokenRepository.delete(resetToken);
            return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Consume the token so it can't be reused
        passwordResetTokenRepository.delete(resetToken);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in with your new password."));
    }
}
