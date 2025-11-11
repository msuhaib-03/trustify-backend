package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "payment_events")
public class PaymentEvent {
    @Id
    private String id;

    private String transactionId; // optional
    private String stripeObjectId; // paymentIntentId or chargeId
    private String type; // "CREATE_INTENT", "WEBHOOK_PAYMENT_INTENT_SUCCEEDED", "RELEASE", "REFUND", etc.
    private String actor; // "SYSTEM", "ADMIN:user", "NODE", etc.
    private Map<String, Object> metadata; // arbitrary details
    @CreatedDate
    private Instant createdAt;
}
