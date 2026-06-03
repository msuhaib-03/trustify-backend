package com.trustify.controller;

import com.trustify.model.Listing;
import com.trustify.model.Transaction;
import com.trustify.repository.DisputeRepository;
import com.trustify.repository.ListingRepository;
import com.trustify.repository.TransactionRepository;
import com.trustify.repository.UserRepository;
import com.trustify.service.AdminService;
import com.trustify.service.CnicVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    @Autowired
    AdminService adminService;

    @Autowired
    DisputeRepository disputeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CnicVerificationService cnicVerificationService;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    ListingRepository listingRepository;

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
    // Fetch disputes for admin
    // Create dispute and refund are in transactions controller with PreAuthorize.
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalUsers = userRepository.count();
        long activeListings = listingRepository.findByStatus(Listing.ListingStatus.ACTIVE).size();
        long totalTransactions = transactionRepository.count();
        long pendingDisputes = disputeRepository.findByStatus("OPEN").size();
        long fraudAlerts = userRepository.findAll().stream()
                .filter(u -> u.getFraudScore() > 70)
                .count();

        // Monthly revenue: sum amountCapturedCents for "paid out" transactions this month
        // Use ZoneOffset.UTC so the LocalDateTime→Instant conversion is unambiguous.
        // Instant.from(LocalDateTime) throws DateTimeException because LocalDateTime has no
        // zone — this was silently fine when the DB was empty (stream never ran the filter).
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Set<Transaction.TransactionStatus> paidStatuses = Set.of(
                Transaction.TransactionStatus.COMPLETED,
                Transaction.TransactionStatus.RELEASED,
                Transaction.TransactionStatus.DELIVERED_AUTO,
                Transaction.TransactionStatus.RENT_COMPLETED,
                Transaction.TransactionStatus.RENTAL_RETURNED
        );
        long monthlyRevenue = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() != null && paidStatuses.contains(t.getStatus()))
                .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(startOfMonth))
                .mapToLong(t -> t.getAmountCapturedCents() != null ? t.getAmountCapturedCents() : t.getAmountCents())
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("activeListings", activeListings);
        result.put("totalTransactions", totalTransactions);
        result.put("pendingDisputes", pendingDisputes);
        result.put("monthlyRevenue", monthlyRevenue);
        result.put("fraudAlerts", fraudAlerts);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transactions/stats")
    public ResponseEntity<?> getTransactionStats() {
        List<Transaction> all = transactionRepository.findAll();

        long totalVolume = all.stream()
                .mapToLong(t -> t.getAmountCapturedCents() != null ? t.getAmountCapturedCents() : t.getAmountCents())
                .sum();

        Set<Transaction.TransactionStatus> pendingStatuses = Set.of(
                Transaction.TransactionStatus.PENDING,
                Transaction.TransactionStatus.AUTHORIZED,
                Transaction.TransactionStatus.HELD,
                Transaction.TransactionStatus.PENDING_RELEASE
        );
        long pendingTransactions = all.stream()
                .filter(t -> t.getStatus() != null && pendingStatuses.contains(t.getStatus()))
                .count();

        Set<Transaction.TransactionStatus> completedStatuses = Set.of(
                Transaction.TransactionStatus.COMPLETED,
                Transaction.TransactionStatus.RELEASED,
                Transaction.TransactionStatus.DELIVERED_AUTO,
                Transaction.TransactionStatus.RENT_COMPLETED,
                Transaction.TransactionStatus.RENTAL_RETURNED
        );
        long completedTransactions = all.stream()
                .filter(t -> t.getStatus() != null && completedStatuses.contains(t.getStatus()))
                .count();

        long disputedTransactions = all.stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.PENDING_DISPUTE)
                .count();
        // Also count open disputes from dispute collection
        long openDisputes = disputeRepository.findByStatus("OPEN").size();
        long totalDisputed = disputedTransactions + openDisputes;

        Map<String, Object> result = new HashMap<>();
        result.put("totalVolume", totalVolume);
        result.put("pendingTransactions", pendingTransactions);
        result.put("completedTransactions", completedTransactions);
        result.put("disputedTransactions", totalDisputed);
        return ResponseEntity.ok(result);
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

    // ========== Admin Transactions View ===========
    @GetMapping("/transactions")
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(transactionRepository.findAll(pageable));
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
