package com.trustify.controller;

import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.trustify.model.Transaction;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final TransactionService transactionService;

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> body) {
        try {
            // parse input
            String productId = (String) body.get("productId");
            String buyerEmail = (String) body.get("buyerEmail");
            String sellerEmail = (String) body.get("sellerEmail");
            String sellerStripeAccountId = (String) body.get("sellerStripeAccountId"); // optional
            long amount = Long.parseLong(String.valueOf(body.get("amount"))); // amount in cents
            String currency = (String) body.getOrDefault("currency", "usd");
            String mode = (String) body.getOrDefault("mode", "SALE");
            Integer days = body.get("daysToRent") != null ? Integer.parseInt(String.valueOf(body.get("daysToRent"))) : null;

            Transaction tx = Transaction.builder()
                    .productId(productId)
                    .buyerEmail(buyerEmail)
                    .sellerEmail(sellerEmail)
                    .sellerStripeAccountId(sellerStripeAccountId)
                    .amount(amount)
                    .currency(currency)
                    .mode(Transaction.Mode.valueOf(mode))
                    .daysToRent(days)
                    .build();

            Transaction created = transactionService.createTransactionAndPaymentIntent(tx);

            // return client secret for frontend confirm
            // fetch the PaymentIntent client_secret from Stripe (we saved only ID)
            PaymentIntent pi = PaymentIntent.retrieve(created.getStripePaymentIntentId());
            return ResponseEntity.ok(Map.of(
                    "transactionId", created.getId(),
                    "clientSecret", pi.getClientSecret(),
                    "paymentIntentId", created.getStripePaymentIntentId()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Admin-only: release escrow (transfer to seller)
    @PostMapping("/release/{transactionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> release(@PathVariable String transactionId) {
        try {
            Transaction updated = transactionService.releaseEscrow(transactionId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // Refund (admin or buyer depending on rules)
    @PostMapping("/refund/{transactionId}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> refund(@PathVariable String transactionId, @RequestBody(required = false) Map<String, Object> body) {
        Long amount = null;
        if (body != null && body.get("amount") != null) {
            amount = Long.parseLong(String.valueOf(body.get("amount")));
        }
        try {
            Transaction refunded = transactionService.refundTransaction(transactionId, amount);
            return ResponseEntity.ok(refunded);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> get(@PathVariable String id) {
        var tx = transactionService.getById(id);
        if (tx == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(tx);
    }
}
