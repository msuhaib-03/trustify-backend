package com.trustify.controller;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.*;
import com.trustify.model.Transaction;
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

        String userId = principal.getName();
        transactionService.requestRelease(id, userId, body != null ? body.get("note") : null);
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

        CaptureResponse resp = transactionService.capture(id, principal.getName(), amountToCaptureCents);
        return ResponseEntity.ok(resp);
    }

    // ---------------- Open Dispute ----------------
    @PostMapping("/{id}/dispute")
    public ResponseEntity<?> openDispute(@PathVariable String id,
                                         @RequestBody DisputeRequest disputeRequest,
                                         Principal principal) {
        transactionService.openDispute(id, principal.getName(), disputeRequest);
        return ResponseEntity.ok(Map.of("message", "Dispute opened"));
    }

    // ---------------- Admin Resolve Dispute ----------------
    @PostMapping("/{id}/admin/resolve-dispute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolveDispute(@PathVariable String id,
                                            @RequestBody ResolveDisputeRequest req,
                                            Principal principal) {
        transactionService.adminResolveDispute(id, principal.getName(), req);
        return ResponseEntity.ok(Map.of("message", "Dispute resolved"));
    }

}
