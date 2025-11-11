package com.trustify.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.trustify.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final TransactionService transactionService;

    @Value("${STRIPE_WEBHOOK_SECRET}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestHeader("Stripe-Signature") String sigHeader,
                                                @RequestBody String payload) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Invalid payload");
        }

        String type = event.getType();

        switch (type) {
            case "payment_intent.succeeded":
            case "payment_intent.amount_capturable_updated": {
                var pi = (com.stripe.model.PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (pi != null) transactionService.handlePaymentIntentSucceeded(pi.getId());
                break;
            }
            case "payment_intent.canceled": {
                var pi = (com.stripe.model.PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (pi != null) transactionService.handlePaymentIntentCancelled(pi.getId());
                break;
            }
            case "charge.refunded": {
                // optionally handle refunds
                break;
            }
            default:
                // log or ignore
        }

        return ResponseEntity.ok("received");
    }

}
