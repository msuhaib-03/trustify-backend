package com.trustify.controller;

import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setup")
public class AdminSetupController {
    // This controller can be used for initial admin setup if needed
    // Admin is created manually for once and now this can be deleted in future and admin is present in DB
    // From postman we have created admin from AdminSetupController without any JSON and loggedin with admin credentials
    // It provided a token to access admin endpoints from dashboard in AdminController
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/create-admin")
    public ResponseEntity<?> createAdmin() {
        // Allow only once: if no ADMIN exists
        if (userRepository.findByRole(User.Role.ADMIN).isPresent()) {
            return ResponseEntity.badRequest().body("Admin already exists! You cannot create another one.");
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@trustify.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole(User.Role.ADMIN);
        admin.setStatus(User.AccountStatus.ACTIVE);

        userRepository.save(admin);
        return ResponseEntity.ok("✅ Admin created successfully!");
    }
}
