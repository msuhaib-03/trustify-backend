package com.trustify.controller;

import com.trustify.repository.DisputeRepository;
import com.trustify.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    AdminService adminService;

    @Autowired
    DisputeRepository disputeRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<String> dashboard() {
        return ResponseEntity.ok("Welcome to the Admin Dashboard");
    }

    // 🔥 Replace useless dashboard
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok("Basic stats later"); // optional
    }

    // Fetch disputes for admin
    // Create dispute and refund are in transactions controller with PreAuthorize.
    @GetMapping("/disputes")
    public ResponseEntity<?> getDisputes(@RequestParam(required = false) String status) {

        if (status != null) {
            return ResponseEntity.ok(disputeRepository.findByStatus(status));
        }

        return ResponseEntity.ok(disputeRepository.findAll());
    }

    @PostMapping("/suspend-user/{userId}")
    public ResponseEntity<?> suspendUser(@PathVariable String userId) {
        adminService.suspendUser(userId);
        return ResponseEntity.ok("User" + userId +"User suspended successfully");
    }
}
