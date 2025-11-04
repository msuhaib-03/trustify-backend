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

import java.util.List;

// FOR USER MANAGEMENT: GET ALL USERS, GET USER BY ID, OR UPDATE USER DETAILS AND ADMIN HAS ACCESS
// TO MOST OF IT

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    // only admin can access this endpoint
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        // Implementation for fetching user by ID can be added here
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // PUT METHOD TO UPDATE USER DETAILS CAN BE ADDED HERE


    // No need for this now because it was created when there was no authentication and we wanted to test
    // our api endpoints quickly. Now signup is handled in AuthController. And moreover we wanted to
    // flood the database with multiple users so kept it here. So need for it now!!
    @PostMapping
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO dto) {
        return userService.createUser(dto);
    }
}
