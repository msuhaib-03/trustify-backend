package com.trustify.controller;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.CaptureResponse;
import com.trustify.dto.CreateTransactionRequest;
import com.trustify.dto.CreateTransactionResult;
import com.trustify.dto.TransactionResponse;
import com.trustify.model.Transaction;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

//        String clientSecret = null;
//        try {
//            com.stripe.Stripe.apiKey = stripeSecret;
//            PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId());
//            clientSecret = pi.getClientSecret();
//        } catch (Exception ignored) {}

        TransactionResponse resp = new TransactionResponse();
        resp.setTransactionId(tx.getId());
        resp.setStripeClientSecret(result.getStripeClientSecret());
        resp.setStripePaymentIntentId(tx.getStripePaymentIntentId());

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<?> release(@PathVariable String id) {
        CaptureResponse captured = transactionService.capture(id);
        return ResponseEntity.ok(captured);
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
}
