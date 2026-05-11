package com.trustify.controller;

import com.trustify.repository.DisputeRepository;
import com.trustify.repository.UserRepository;
import com.trustify.service.AdminService;
import com.trustify.service.CnicVerificationService;
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

    @Autowired
    UserRepository userRepository;

    @Autowired
    CnicVerificationService cnicVerificationService;

    @GetMapping("/dashboard")
    public ResponseEntity<String> dashboard() {
        return ResponseEntity.ok("Welcome to the Admin Dashboard");
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(
                userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"))
        );
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

    // Fraud score + Rating
    @GetMapping("/users/high-risk")
    public ResponseEntity<?> getHighRiskUsers() {
        return ResponseEntity.ok(
                userRepository.findByFraudScoreGreaterThan(70)
        );
    }

    @PostMapping("/suspend-user/{userId}")
    public ResponseEntity<?> suspendUser(@PathVariable String userId) {
        adminService.suspendUser(userId);
        return ResponseEntity.ok("User" + userId +"User suspended successfully");
    }

    //  ========== Admin APIs for CNIC Verification ==============
    @GetMapping("/cnic/pending")
    public ResponseEntity<?> getPendingVerifications() {
        return ResponseEntity.ok(cnicVerificationService.getPendingVerifications());
    }

    @PostMapping("/cnic/{id}/approve")
    public ResponseEntity<?> approveCnic(@PathVariable String id){
        return ResponseEntity.ok(cnicVerificationService.approveVerification(id));
    }

    @PostMapping("/cnic/{id}/reject")
    public ResponseEntity<?> rejectCnic(@PathVariable String id){
        return ResponseEntity.ok(cnicVerificationService.rejectVerification(id));
    }

    @GetMapping("/cnic/all")
    public ResponseEntity<?> getAllCnics(){
        return ResponseEntity.ok(cnicVerificationService.getAllVerifications());
    }
}
