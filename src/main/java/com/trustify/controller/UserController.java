package com.trustify.controller;

import com.trustify.dto.UserRequestDTO;
import com.trustify.dto.UserResponseDTO;
import com.trustify.model.User;
import com.trustify.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

// FOR USER MANAGEMENT: GET ALL USERS, GET USER BY ID, OR UPDATE USER DETAILS AND ADMIN HAS ACCESS
// TO MOST OF IT

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    UserService userService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/all")
    // only admin can access this endpoint
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','USER')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        // Implementation for fetching user by ID can be added here
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // PUT METHOD TO UPDATE USER DETAILS CAN BE ADDED HERE
    /** Update the authenticated user's password. Body: { "newPassword": "..." } */
    @PutMapping("/me/password")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN')")
    public ResponseEntity<?> updatePassword(
            @RequestBody Map<String, String> body,
            Principal principal) {
        try {
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "newPassword is required"));
            }
            userService.updatePassword(principal.getName(), newPassword);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    // No need for this now because it was created when there was no authentication and we wanted to test
    // our api endpoints quickly. Now signup is handled in AuthController. And moreover we wanted to
    // flood the database with multiple users so kept it here. So need for it now!!
    @PostMapping
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO dto) {
        return userService.createUser(dto);
    }
}
