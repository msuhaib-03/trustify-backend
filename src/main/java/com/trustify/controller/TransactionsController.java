package com.trustify.controller;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.*;
import com.trustify.model.Transaction;
import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionsController {

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecret;

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    /**
     * Resolves the principal's email to their MongoDB ObjectId.
     * This ensures service methods that compare against stored ObjectIds
     * always receive a consistent ID format, regardless of whether the
     * transaction stored buyerId/sellerId as an email or an ObjectId.
     */
    private String resolveUserId(Principal principal) {
        String email = principal.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(email); // fall back to email if lookup fails
    }

    // List all transactions where the authenticated user is buyer or seller
    @GetMapping
    public ResponseEntity<?> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(transactionService.listForUser(principal.getName(), pageable));
    }

    // payment intent creation and authorization
    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody CreateTransactionRequest req) {
        CreateTransactionResult result = transactionService.createAndAuthorize(req);
        Transaction tx = result.getTransaction();

        TransactionResponse resp = new TransactionResponse();
        resp.setTransactionId(tx.getId());
        resp.setStripeClientSecret(result.getStripeClientSecret());
        resp.setStripePaymentIntentId(tx.getStripePaymentIntentId());

        return ResponseEntity.ok(resp);
    }


    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> refund(@PathVariable String id, @RequestParam(required = false) Long amountCents) {
        transactionService.refund(id, amountCents);
        return ResponseEntity.ok("Refund initiated");
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        Transaction tx = transactionService.getTransaction(id);
        return ResponseEntity.ok(tx);
    }

    // ------------------------------------------------------ //

    // ---------------- Request Release (Buyer initiates inspection / asks for release) ----------------
    @PostMapping("/{id}/request-release")
    public ResponseEntity<?> requestRelease(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {

        transactionService.requestRelease(id, resolveUserId(principal), body != null ? body.get("note") : null);
        return ResponseEntity.ok(Map.of("message", "Release requested"));
    }


    // ---------------- Confirm Release (Admin / Seller finalizes) ----------------
    @PostMapping("/{id}/confirm-release")
    public ResponseEntity<?> confirmRelease(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal) {

        Long amountToCaptureCents = null;
        if (body != null && body.get("amountToCaptureCents") != null) {
            amountToCaptureCents = Long.valueOf(body.get("amountToCaptureCents").toString());
        }

        CaptureResponse resp = transactionService.capture(id, resolveUserId(principal), amountToCaptureCents);
        return ResponseEntity.ok(resp);
    }

    // ---------------- Open Dispute ----------------
    @PostMapping("/{id}/dispute")
    public ResponseEntity<?> openDispute(@PathVariable String id,
                                         @RequestBody DisputeRequest disputeRequest,
                                         Principal principal) {
        transactionService.openDispute(id, resolveUserId(principal), disputeRequest);
        return ResponseEntity.ok(Map.of("message", "Dispute opened"));
    }

    // ---------------- Admin Resolve Dispute ----------------
    @PostMapping("/{id}/admin/resolve-dispute")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> resolveDispute(@PathVariable String id,
                                            @RequestBody ResolveDisputeRequest req,
                                            Principal principal) {
        transactionService.adminResolveDispute(id, principal.getName(), req);
        return ResponseEntity.ok(Map.of("message", "Dispute resolved"));
    }

    // ----------------- RENTAL SPECIFIC ACTIONS -----------------
    @PostMapping("/{id}/start-rental")
    public ResponseEntity<?> startRental(@PathVariable String id, Principal principal) {
        // resolveUserId converts the JWT email to the user's MongoDB ObjectId.
        // This ensures isTheBuyer() in the service matches directly (step 1)
        // without relying on the email→lookup→ObjectId chain, which can fail
        // for older transactions where buyerEmail was null.
        transactionService.startRental(id, resolveUserId(principal));
        return ResponseEntity.ok(Map.of("message", "Rental started"));
    }

    @PostMapping("/{id}/complete-rental")
    public ResponseEntity<?> completeRental(@PathVariable String id, Principal principal) {
        transactionService.completeRental(id, resolveUserId(principal));
        return ResponseEntity.ok(Map.of("message", "Rental completed"));
    }

    @PostMapping("/{id}/deduct-damage")
    public ResponseEntity<?> deductDamage(
            @PathVariable String id,
            @RequestParam Long damageAmountCents,
            Principal principal) {
        transactionService.deductDamage(id, damageAmountCents,resolveUserId(principal));
        return ResponseEntity.ok(Map.of("message", "Damage processed"));
    }

    @PostMapping("/{id}/finalize-refund")
    public ResponseEntity<?> finalizeRefund(@PathVariable String id, Principal principal) {
        transactionService.finalizeRefund(id, resolveUserId(principal));
        return ResponseEntity.ok(Map.of("message", "Rental finalized - Payment released"));
    }

    // ===== CONDITION ACCEPTANCE =====
    @PostMapping("/{id}/accept-conditions")
    public ResponseEntity<?> acceptConditions(@PathVariable String id, @RequestParam String userId){
        transactionService.acceptedConditions(id, userId);
        return ResponseEntity.ok(Map.of("message"," Conditions accepted"));
    }


}



// All the endpoints are secured, therefore will require a valid bearer token in the Authorization header.
// First hit this endpoint with valid bearer token:
// Make sure that the body contains the same email/mail that user logged in from.
// http://localhost:8080/api/transactions

// Then this endpoint with bearer token and paymentintent id from above response:
// https://api.stripe.com/v1/payment_intents/pi_id/confirm

// Then this endpoint with bearer token and transaction id from first response:
// http://localhost:8080/api/transactions/transaction-id/request-release

// Then this endpoint with bearer token and transaction id from first response:
// http://localhost:8080/api/transactions/transaction-id/confirm-release

// Then finally this endpoint with bearer token and transaction id from first response:
// http://localhost:8080/api/transactions/transaction-id/refund?amountCents=<amount-to-refund>

// ------------------------------------>>>>>>>>>>> ENDPOINTS FOR SELL AND RENT ARE DIFFERENT


