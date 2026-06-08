package com.trustify.controller;


import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.trustify.chat.repository.ChatRepository;
import com.trustify.model.CategoryDepositConfig;
import com.trustify.model.Dispute;
import com.trustify.model.Listing;
import com.trustify.model.Transaction;
import com.trustify.repository.*;
import com.trustify.service.AdminService;
import com.trustify.service.CnicVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

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

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    CategoryDepositConfigRepository depositConfigRepository;

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecret;

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


    // ========== Admin Chat Monitoring ===========
    /**
     * Returns a lightweight summary of every chat — avoids full Chat/Message
     * deserialization which can 500 on malformed legacy documents.
     */
    @GetMapping("/chats")
    public ResponseEntity<?> getAllChats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            // Load all chat IDs + minimal fields via the raw repository.
            // We build the summary manually so that a single bad document
            // never brings down the whole response.
            List<Map<String, Object>> summaries = new ArrayList<>();
            chatRepository.findAll().forEach(chat -> {
                try {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", chat.getId());
                    // participants is a Set<String> — safe to add directly
                    dto.put("participants",
                            chat.getParticipants() != null ? new ArrayList<>(chat.getParticipants()) : Collections.emptyList());
                    dto.put("updatedAt", chat.getUpdatedAt() != null ? chat.getUpdatedAt().toString() : null);
                    int msgCount = chat.getMessages() != null ? chat.getMessages().size() : 0;
                    dto.put("messageCount", msgCount);
                    if (msgCount > 0) {
                        var last = chat.getMessages().get(msgCount - 1);
                        dto.put("lastMessage", last.getContent());
                        dto.put("lastMessageAt", last.getTimestamp() != null ? last.getTimestamp().toString() : null);
                    }
                    summaries.add(dto);
                } catch (Exception ignored) {
                    // skip malformed documents
                }
            });

            // Sort by updatedAt desc
            summaries.sort((a, b) -> {
                String ta = (String) a.get("updatedAt");
                String tb = (String) b.get("updatedAt");
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });

            // Manual pagination
            int total = summaries.size();
            int from = Math.min(page * size, total);
            int to   = Math.min(from + size, total);
            List<Map<String, Object>> pageContent = summaries.subList(from, to);

            Map<String, Object> response = new HashMap<>();
            response.put("content", pageContent);
            response.put("totalElements", total);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) total / size) : 1);
            response.put("currentPage", page);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to load chats: " + e.getMessage()));
        }
    }

    // ========== Category Deposit Configuration ===========

    private static final List<Map<String, Object>> DEFAULT_CONFIGS = List.of(
            Map.of("category", "Electronics", "depositPercentage", 90),
            Map.of("category", "Furniture",   "depositPercentage", 70),
            Map.of("category", "Books",        "depositPercentage", 80),
            Map.of("category", "Sports",       "depositPercentage", 60),
            Map.of("category", "Fashion",      "depositPercentage", 50),
            Map.of("category", "Other",        "depositPercentage", 50)
    );

    /**
     * Returns all category deposit configurations.
     * Auto-seeds defaults on first call if the collection is empty.
     */
    @GetMapping("/deposit-config")
    public ResponseEntity<?> getDepositConfig() {
        List<CategoryDepositConfig> configs = depositConfigRepository.findAll();
        if (configs.isEmpty()) {
            // Seed defaults
            DEFAULT_CONFIGS.forEach(d -> depositConfigRepository.save(
                    CategoryDepositConfig.builder()
                            .category((String) d.get("category"))
                            .depositPercentage((int) d.get("depositPercentage"))
                            .build()
            ));
            configs = depositConfigRepository.findAll();
        }
        return ResponseEntity.ok(configs);
    }

    // ========== System Health Metrics ===========

    /**
     * Returns live system health metrics for the admin dashboard.
     * - memoryUsagePct : real JVM heap usage
     * - dbLoadPct      : proxy based on total collection document counts
     * - fraudRate      : % of clean (non-dispute, non-refunded) transactions
     * - aiAccuracy     : static mock value (fraud model is simulated)
     */
    @GetMapping("/system-health")
    public ResponseEntity<?> getSystemHealth() {
        Runtime rt  = Runtime.getRuntime();
        long used   = rt.totalMemory() - rt.freeMemory();
        long max    = rt.maxMemory();
        long memPct = Math.round((double) used / max * 100.0);

        long totalUsers    = userRepository.count();
        long totalTx       = transactionRepository.count();
        long totalListings = listingRepository.count();
        long totalDocs     = totalUsers + totalTx + totalListings;
        // 500 docs ≈ 100% load (scales nicely for a project-size DB)
        long dbLoadPct = Math.min(Math.round(totalDocs / 5.0), 100L);

        // Fraud rate = percentage of transactions that did NOT end in dispute/refund
        long badTx = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.REFUNDED
                        || t.getStatus() == Transaction.TransactionStatus.PENDING_DISPUTE
                        || t.getStatus() == Transaction.TransactionStatus.CANCELLED)
                .count();
        double fraudRate = totalTx > 0 ? (1.0 - (double) badTx / totalTx) * 100.0 : 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("memoryUsagePct", memPct);
        result.put("dbLoadPct",      dbLoadPct);
        result.put("fraudRate",      Math.round(fraudRate * 10.0) / 10.0);
        result.put("aiAccuracy",     94.2);           // mock model — fixed
        return ResponseEntity.ok(result);
    }

    // ========== Monthly Revenue Breakdown ===========

    /**
     * Returns per-month transaction volume for the last 12 months.
     * Used to populate the Revenue Overview chart.
     */
    @GetMapping("/revenue/monthly")
    public ResponseEntity<?> getMonthlyRevenue() {
        List<Transaction> all = transactionRepository.findAll();

        // Only count finalised/paid transactions
        Set<Transaction.TransactionStatus> paidStatuses = Set.of(
                Transaction.TransactionStatus.COMPLETED,
                Transaction.TransactionStatus.RELEASED,
                Transaction.TransactionStatus.DELIVERED_AUTO,
                Transaction.TransactionStatus.RENT_COMPLETED,
                Transaction.TransactionStatus.RENTAL_RETURNED
        );

        java.time.YearMonth now = java.time.YearMonth.now();
        // Build ordered map for last 12 months (oldest first)
        java.util.LinkedHashMap<String, Long> monthMap = new java.util.LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            java.time.YearMonth ym = now.minusMonths(i);
            monthMap.put(ym.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH), 0L);
        }

        all.stream()
                .filter(t -> t.getStatus() != null && paidStatuses.contains(t.getStatus()))
                .filter(t -> t.getCreatedAt() != null)
                .forEach(t -> {
                    java.time.YearMonth txYm = java.time.YearMonth.from(
                            t.getCreatedAt().atZone(ZoneOffset.UTC));
                    // Only within our 12-month window
                    if (!txYm.isBefore(now.minusMonths(11)) && !txYm.isAfter(now)) {
                        String key = txYm.getMonth().getDisplayName(
                                java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
                        long amt = t.getAmountCapturedCents() != null
                                ? t.getAmountCapturedCents() : t.getAmountCents();
                        monthMap.merge(key, amt, Long::sum);
                    }
                });

        List<Map<String, Object>> result = new ArrayList<>();
        monthMap.forEach((month, total) -> {
            Map<String, Object> point = new HashMap<>();
            point.put("name",  month);
            point.put("value", total);
            result.add(point);
        });
        return ResponseEntity.ok(result);
    }

    // ========== Recent Activity Feed ===========

    /**
     * Returns the latest 10 platform events assembled from transactions and disputes.
     * Sorted newest-first.
     */
    @GetMapping("/activity/recent")
    public ResponseEntity<?> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit) {

        List<Map<String, Object>> events = new ArrayList<>();

        // Transactions → convert to activity events
        transactionRepository.findAll().forEach(t -> {
            try {
                String action; String type;
                if (t.getStatus() == Transaction.TransactionStatus.PENDING_DISPUTE) {
                    action = "Dispute opened on transaction"; type = "DISPUTE";
                } else if (t.getStatus() == Transaction.TransactionStatus.REFUNDED) {
                    action = "Payment refunded to buyer"; type = "TRANSACTION";
                } else if (t.getStatus() == Transaction.TransactionStatus.COMPLETED
                        || t.getStatus() == Transaction.TransactionStatus.RELEASED
                        || t.getStatus() == Transaction.TransactionStatus.RENT_COMPLETED) {
                    action = "Payment completed"; type = "TRANSACTION";
                } else if (t.getStatus() == Transaction.TransactionStatus.AUTHORIZED
                        || t.getStatus() == Transaction.TransactionStatus.HELD) {
                    action = "Payment initiated — funds in escrow"; type = "TRANSACTION";
                } else {
                    action = "Transaction " + t.getStatus(); type = "TRANSACTION";
                }
                Map<String, Object> e = new HashMap<>();
                e.put("type",      type);
                e.put("action",    action);
                e.put("user",      t.getBuyerEmail() != null ? t.getBuyerEmail() : t.getBuyerId());
                e.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
                events.add(e);
            } catch (Exception ignored) {}
        });

        // Disputes → separate entries
        disputeRepository.findAll().forEach(d -> {
            try {
                Map<String, Object> e = new HashMap<>();
                e.put("type",      "DISPUTE");
                e.put("action",    "Dispute " + (d.getStatus() != null ? d.getStatus().toLowerCase() : "opened"));
                e.put("user",      d.getOpenedBy() != null ? d.getOpenedBy() : "unknown");
                e.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : "");
                events.add(e);
            } catch (Exception ignored) {}
        });

        // High-risk users → fraud alerts in activity
        userRepository.findAll().stream().filter(u -> u.getFraudScore() > 70).forEach(u -> {
            try {
                Map<String, Object> e = new HashMap<>();
                e.put("type",      "FRAUD");
                e.put("action",    "High-risk user flagged (score " + u.getFraudScore() + ")");
                e.put("user",      u.getEmail());
                e.put("createdAt", Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString());
                events.add(e);
            } catch (Exception ignored) {}
        });

        // Sort newest first, cap at limit
        events.sort((a, b) -> {
            String ta = (String) a.get("createdAt");
            String tb = (String) b.get("createdAt");
            if (ta == null || ta.isEmpty()) return 1;
            if (tb == null || tb.isEmpty()) return -1;
            return tb.compareTo(ta);
        });

        return ResponseEntity.ok(events.subList(0, Math.min(limit, events.size())));
    }

    // ========== Admin Force-Complete (stuck transaction recovery) ===========

    /**
     * Force-completes a rental transaction that is stuck at PARTIALLY_RELEASED or
     * any intermediate state where Stripe has already settled but the DB status
     * was never advanced to RENT_COMPLETED.
     *
     * Use when: seller's "Mark Returned" button has disappeared (e.g. after a
     * non-critical post-capture exception corrupted the status) and the normal
     * finalizeRefund flow is unreachable from the UI.
     */
    @PostMapping("/transactions/{id}/force-complete")
    public ResponseEntity<?> forceCompleteTransaction(@PathVariable String id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));

        Transaction.TransactionStatus prev = tx.getStatus();
        tx.setStatus(Transaction.TransactionStatus.RENT_COMPLETED);
        tx.setUpdatedAt(java.time.Instant.now());
        transactionRepository.save(tx);

        // Sync listing status: a forced-complete rental returns the item to the platform;
        // a forced-complete sale removes it permanently.
        if (tx.getListingId() != null) {
            listingRepository.findById(tx.getListingId()).ifPresent(l -> {
                Listing.ListingStatus newStatus = tx.getType() == Transaction.TransactionType.RENT
                        ? Listing.ListingStatus.ACTIVE   // rental done — item available again
                        : Listing.ListingStatus.SOLD;    // sale completed — item gone
                l.setStatus(newStatus);
                listingRepository.save(l);
            });
        }

        return ResponseEntity.ok(Map.of(
                "message", "Transaction force-completed by admin",
                "id", id,
                "previousStatus", prev != null ? prev.name() : "null",
                "newStatus", "RENT_COMPLETED"
        ));
    }

    /**
     * Bulk-update deposit percentages.
     * Body: [ { "category": "Electronics", "depositPercentage": 90 }, ... ]
     */
    @PutMapping("/deposit-config")
    public ResponseEntity<?> updateDepositConfig(@RequestBody List<Map<String, Object>> updates) {
        for (Map<String, Object> update : updates) {
            String category = (String) update.get("category");
            int pct = ((Number) update.get("depositPercentage")).intValue();
            if (pct < 0 || pct > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "Percentage must be 0–100 for category: " + category));
            }
            CategoryDepositConfig cfg = depositConfigRepository.findByCategory(category)
                    .orElse(CategoryDepositConfig.builder().category(category).build());
            cfg.setDepositPercentage(pct);
            cfg.setUpdatedAt(Instant.now());
            depositConfigRepository.save(cfg);
        }
        return ResponseEntity.ok(depositConfigRepository.findAll());
    }

    /**
     * Admin resolves a dispute.
     * Body: { "action": "refund_buyer"|"release_to_seller"|"partial_refund"|"update_status",
     *         "resolution": "...",
     *         "refundAmountCents": 500   // only for partial_refund
     *       }
     *
     * Stripe behaviour:
     *   refund_buyer      — cancel PaymentIntent if still uncaptured; else create full Refund
     *   release_to_seller — capture the PaymentIntent (full amount)
     *   partial_refund    — capture full then immediately refund partial
     *   update_status     — DB/status change only, no Stripe call
     */
    @PostMapping("/disputes/{id}/resolve")
    public ResponseEntity<?> resolveDispute(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        Stripe.apiKey = stripeSecret;

        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dispute not found: " + id));

        String action     = (String) body.getOrDefault("action", "update_status");
        String resolution = (String) body.getOrDefault("resolution", "");
        Number refundAmtObj = (Number) body.get("refundAmountCents");
        long   refundAmountCents = refundAmtObj != null ? refundAmtObj.longValue() : 0L;

        // Fetch associated transaction (may be null if dispute has no transactionId)
        Transaction tx = null;
        if (dispute.getTransactionId() != null) {
            tx = transactionRepository.findById(dispute.getTransactionId()).orElse(null);
        }

        String stripeResult = "no_stripe_action";

        try {
            if ("update_status".equals(action)) {
                // DB-only: just flip dispute to UNDER_REVIEW (caller decides)
                dispute.setStatus("UNDER_REVIEW");

            } else if ("refund_buyer".equals(action)) {
                if (tx != null && tx.getStripePaymentIntentId() != null) {
                    PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId());
                    String piStatus = pi.getStatus();

                    if ("requires_capture".equals(piStatus) || "requires_payment_method".equals(piStatus)
                            || "requires_confirmation".equals(piStatus) || "requires_action".equals(piStatus)) {
                        // Not yet captured — just cancel it
                        pi.cancel();
                        stripeResult = "payment_intent_cancelled";
                    } else if ("succeeded".equals(piStatus)) {
                        // Already captured — issue a full refund
                        Refund.create(RefundCreateParams.builder()
                                .setPaymentIntent(tx.getStripePaymentIntentId())
                                .build());
                        stripeResult = "full_refund_issued";
                    } else {
                        stripeResult = "pi_status_" + piStatus + "_no_action_taken";
                    }
                }
                if (tx != null) {
                    tx.setStatus(Transaction.TransactionStatus.REFUNDED);
                    tx.setUpdatedAt(Instant.now());
                    transactionRepository.save(tx);
                    // Buyer refunded — item never changed hands; restore to available
                    if (tx.getListingId() != null) {
                        listingRepository.findById(tx.getListingId()).ifPresent(l -> {
                            l.setStatus(Listing.ListingStatus.ACTIVE);
                            listingRepository.save(l);
                        });
                    }
                }
                dispute.setStatus("RESOLVED");

            } else if ("release_to_seller".equals(action)) {
                if (tx != null && tx.getStripePaymentIntentId() != null) {
                    PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId());
                    if ("requires_capture".equals(pi.getStatus())) {
                        pi.capture();
                        stripeResult = "payment_intent_captured";
                    } else {
                        stripeResult = "pi_status_" + pi.getStatus() + "_no_capture_needed";
                    }
                }
                if (tx != null) {
                    tx.setStatus(Transaction.TransactionStatus.RELEASED);
                    tx.setUpdatedAt(Instant.now());
                    transactionRepository.save(tx);
                    // Payment released to seller: if sale → mark SOLD; if rent → item available again
                    if (tx.getListingId() != null) {
                        final Transaction.TransactionType txType = tx.getType();
                        final String txListingId = tx.getListingId();
                        listingRepository.findById(txListingId).ifPresent(l -> {
                            Listing.ListingStatus newStatus = txType == Transaction.TransactionType.SALE
                                    ? Listing.ListingStatus.SOLD
                                    : Listing.ListingStatus.ACTIVE;
                            l.setStatus(newStatus);
                            listingRepository.save(l);
                        });
                    }
                }
                dispute.setStatus("RESOLVED");

            } else if ("partial_refund".equals(action)) {
                if (tx != null && tx.getStripePaymentIntentId() != null && refundAmountCents > 0) {
                    PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId());
                    if ("requires_capture".equals(pi.getStatus())) {
                        pi.capture();
                    }
                    Refund.create(RefundCreateParams.builder()
                            .setPaymentIntent(tx.getStripePaymentIntentId())
                            .setAmount(refundAmountCents)
                            .build());
                    stripeResult = "partial_refund_of_" + refundAmountCents + "_cents";
                }
                if (tx != null) {
                    tx.setStatus(Transaction.TransactionStatus.REFUNDED);
                    tx.setUpdatedAt(Instant.now());
                    transactionRepository.save(tx);
                    // Partial refund — transaction closed; restore listing to available
                    if (tx.getListingId() != null) {
                        listingRepository.findById(tx.getListingId()).ifPresent(l -> {
                            l.setStatus(Listing.ListingStatus.ACTIVE);
                            listingRepository.save(l);
                        });
                    }
                }
                dispute.setStatus("RESOLVED");
                dispute.setRefundAmountCents(refundAmountCents);
            }
        } catch (StripeException e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Stripe error: " + e.getMessage(), "stripeCode", e.getCode()));
        }

        dispute.setResolutionNote(resolution);
        dispute.setDecision(action);
        dispute.setResolvedAt(Instant.now());
        disputeRepository.save(dispute);

        return ResponseEntity.ok(Map.of(
                "message", "Dispute resolved",
                "disputeId", id,
                "action", action,
                "stripeResult", stripeResult,
                "disputeStatus", dispute.getStatus()
        ));
    }
}
