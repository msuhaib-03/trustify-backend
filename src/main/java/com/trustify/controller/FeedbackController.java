package com.trustify.controller;

import com.trustify.model.Feedback;
import com.trustify.model.User;
import com.trustify.repository.FeedbackRepository;
import com.trustify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/feedback")
@RestController
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────
    // User-facing endpoints (any authenticated user)
    // ─────────────────────────────────────────

    /** POST /feedback — user submits feedback */
    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody Map<String, Object> body,
                                            Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String feedbackTypeStr = (String) body.get("feedbackType");
        Feedback.FeedbackType feedbackType;
        try {
            feedbackType = Feedback.FeedbackType.valueOf(feedbackTypeStr);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid feedbackType: " + feedbackTypeStr));
        }

        Integer rating = null;
        if (body.get("rating") != null) {
            rating = ((Number) body.get("rating")).intValue();
        }

        Feedback feedback = Feedback.builder()
                .userId(user.getId())
                .userName(user.getUsername() != null ? user.getUsername() : user.getEmail())
                .userEmail(user.getEmail())
                .feedbackType(feedbackType)
                .rating(rating)
                .title((String) body.get("title"))
                .message((String) body.get("message"))
                .status(Feedback.FeedbackStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        return ResponseEntity.ok(saved);
    }

    /** GET /feedback/user — current user's own feedback history */
    @GetMapping("/user")
    public ResponseEntity<?> getUserFeedback(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Feedback> list = feedbackRepository
                .findByUserId(user.getId(), PageRequest.of(0, 200, Sort.by("createdAt").descending()))
                .getContent();

        Map<String, Object> result = new HashMap<>();
        result.put("content", list);
        result.put("totalElements", list.size());
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────
    // Admin-facing endpoints
    // ─────────────────────────────────────────

    /** GET /feedback — paginated list of all feedback (admin) */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Feedback> feedbackPage;

        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
            try {
                feedbackPage = feedbackRepository.findByStatus(
                        Feedback.FeedbackStatus.valueOf(status.toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                feedbackPage = feedbackRepository.findAll(pageable);
            }
        } else {
            feedbackPage = feedbackRepository.findAll(pageable);
        }

        return ResponseEntity.ok(feedbackPage);
    }

    /** GET /feedback/stats — aggregate feedback statistics (admin) */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getStats() {
        long total      = feedbackRepository.count();
        long newCount   = feedbackRepository.countByStatus(Feedback.FeedbackStatus.NEW);
        long reviewed   = feedbackRepository.countByStatus(Feedback.FeedbackStatus.REVIEWED);
        long resolved   = feedbackRepository.countByStatus(Feedback.FeedbackStatus.RESOLVED);

        double avgRating = feedbackRepository.findAll().stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFeedback",    total);
        stats.put("newFeedback",      newCount);
        stats.put("reviewedFeedback", reviewed);
        stats.put("resolvedFeedback", resolved);
        stats.put("averageRating",    Math.round(avgRating * 10.0) / 10.0);
        return ResponseEntity.ok(stats);
    }

    /** PUT /feedback/{id}/status — admin updates feedback status */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                          @RequestBody Map<String, String> body) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found: " + id));

        try {
            feedback.setStatus(Feedback.FeedbackStatus.valueOf(body.get("status").toUpperCase()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + body.get("status")));
        }

        feedback.setUpdatedAt(Instant.now());
        feedbackRepository.save(feedback);
        return ResponseEntity.ok(feedback);
    }
}
