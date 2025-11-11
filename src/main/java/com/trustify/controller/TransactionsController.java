package com.trustify.controller;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.CreateTransactionRequest;
import com.trustify.dto.TransactionResponse;
import com.trustify.model.Transaction;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionsController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody CreateTransactionRequest req) {
        Transaction tx = transactionService.createAndAuthorize(req);
        // return clientSecret if you used a client-side confirm flow — retrieve PI to get client_secret:
        String clientSecret = null;
        try {
            com.stripe.Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY"); // or from config
            PaymentIntent pi = com.stripe.model.PaymentIntent.retrieve(tx.getStripePaymentIntentId());
            clientSecret = pi.getClientSecret();
        } catch (Exception e) {
            // ignore - still return tx id
        }

        TransactionResponse resp = new TransactionResponse();
        resp.setTransactionId(tx.getId());
        resp.setStripeClientSecret(clientSecret);
        resp.setStripePaymentIntentId(tx.getStripePaymentIntentId());

        return ResponseEntity.ok(resp);
    }


    @PostMapping("/{id}/release")
    public ResponseEntity<?> release(@PathVariable String id) {
        PaymentIntent captured = transactionService.capture(id);
        return ResponseEntity.ok(captured);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(@PathVariable String id, @RequestParam(required = false) Long amountCents) {
        transactionService.refund(id, amountCents);
        return ResponseEntity.ok("Refund initiated");
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return transactionService.getClass(); // quick placeholder (implement a get in service or repo)
    }
}
