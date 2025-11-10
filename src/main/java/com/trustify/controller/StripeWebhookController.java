package com.trustify.controller;

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
    public ResponseEntity<String> handleWebhook(HttpServletRequest request, @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            String payload = new BufferedReader(request.getReader()).lines().collect(Collectors.joining("\n"));

            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            switch (event.getType()) {
                case "payment_intent.succeeded": {
                    // event.getDataObjectDeserializer() might return an incomplete object depending on the SDK version.
                    // So extract the id and retrieve full PaymentIntent from Stripe.
                    com.stripe.model.PaymentIntent webhookPi = (PaymentIntent) event.getDataObjectDeserializer()
                            .getObject()
                            .orElse(null);

                    if (webhookPi != null) {
                        String piId = webhookPi.getId();
                        // fetch full PI from Stripe to be safe
                        PaymentIntent fullPi = PaymentIntent.retrieve(piId);

                        String chargeId = null;
                        if (fullPi.getCharges() != null && !fullPi.getCharges().getData().isEmpty()) {
                            Charge ch = (Charge) fullPi.getCharges().getData().get(0);
                            chargeId = ch.getId();
                        }
                        transactionService.handlePaymentIntentSucceeded(piId, chargeId);
                    }
                    break;
                }
            }    // handle other events you care about


            return ResponseEntity.ok("{\"status\":\"received\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body("{\"error\":\"invalid webhook\"}");
        }
    }
}
