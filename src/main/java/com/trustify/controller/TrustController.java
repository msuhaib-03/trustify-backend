package com.trustify.controller;


import com.trustify.dto.TrustProfileResponse;
import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/trust")
@RequiredArgsConstructor
public class TrustController {
    private final UserRepository userRepository;

    // ✅ GET /trust/profile — current authenticated user's trust profile
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        User user = userRepository.findByEmail(principal.getName())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        return ResponseEntity.ok(buildProfile(user));
    }

    // ✅ GET /trust/profile/{userId} — any user's trust profile
    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        return ResponseEntity.ok(buildProfile(user));
    }

    // ✅ GET /trust/users — all users with trust data (admin)
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<TrustProfileResponse> profiles = userRepository.findAll()
                .stream()
                .map(this::buildProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(profiles);
    }

    // ✅ GET /trust/users/high-risk — users with fraudScore > 50 (admin)
    @GetMapping("/users/high-risk")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getHighRiskUsers() {
        List<TrustProfileResponse> highRisk = userRepository.findAll()
                .stream()
                .filter(u -> u.getFraudScore() != null && u.getFraudScore() > 50)
                .map(this::buildProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(highRisk);
    }

    // ✅ GET /trust/stats — aggregate trust statistics (admin)
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getStats() {
        List<User> allUsers = userRepository.findAll();
        long safe     = allUsers.stream().filter(u -> getRiskLevel(u.getFraudScore()).equals("SAFE")).count();
        long medium   = allUsers.stream().filter(u -> getRiskLevel(u.getFraudScore()).equals("MEDIUM")).count();
        long high     = allUsers.stream().filter(u -> getRiskLevel(u.getFraudScore()).equals("HIGH")).count();
        long critical = allUsers.stream().filter(u -> getRiskLevel(u.getFraudScore()).equals("CRITICAL")).count();
        double avgRating = allUsers.stream()
                .mapToDouble(u -> u.getTrustRating() != null ? u.getTrustRating() : 5.0)
                .average().orElse(5.0);

        return ResponseEntity.ok(Map.of(
                "safeUsers", safe,
                "mediumRiskUsers", medium,
                "highRiskUsers", high,
                "criticalUsers", critical,
                "totalUsers", allUsers.size(),
                "averageTrustRating", Math.round(avgRating * 100.0) / 100.0
        ));
    }

    // ─────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────

    private TrustProfileResponse buildProfile(User user) {
        int fraudScore = user.getFraudScore() != null ? user.getFraudScore() : 0;
        double trustRating = user.getTrustRating() != null ? user.getTrustRating() : 5.0;
        int disputes = user.getDisputeCount() != null ? user.getDisputeCount() : 0;
        int total = user.getTotalTransactions() != null ? user.getTotalTransactions() : 0;
        int successful = user.getSuccessfulTransactions() != null ? user.getSuccessfulTransactions() : 0;

        return TrustProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .fraudScore(fraudScore)
                .trustRating(trustRating)
                .riskLevel(getRiskLevel(fraudScore))
                .trustBadge(getTrustBadge(trustRating))
                .verified(user.isVerified())
                .disputeCount(disputes)
                .totalTransactions(total)
                .successfulTransactions(successful)
                .build();
    }

    /** Maps fraudScore → SAFE / MEDIUM / HIGH / CRITICAL */
    private String getRiskLevel(Integer fraudScore) {
        if (fraudScore == null || fraudScore <= 20) return "SAFE";
        if (fraudScore <= 50) return "MEDIUM";
        if (fraudScore <= 80) return "HIGH";
        return "CRITICAL";
    }

    /** Maps trustRating → TRUSTED / NORMAL / RISKY */
    private String getTrustBadge(double trustRating) {
        if (trustRating >= 4.5) return "TRUSTED";
        if (trustRating >= 3.0) return "NORMAL";
        return "RISKY";
    }
}
