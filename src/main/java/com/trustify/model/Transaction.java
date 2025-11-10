package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

@Document(collection = "transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {
    @Id
    private String id;

    private String productId;
    private String buyerEmail;
    private String sellerEmail;
    private String sellerStripeAccountId; // optional (for Stripe Connect)

    private Long amount; // in cents
    private String currency; // "usd" or "pkr" etc.
    private String stripePaymentIntentId;
    private String stripeChargeId; // available after success

    private Integer daysToRent; // optional for rent
    private Mode mode; // SALE or RENT

    private Status status;

    @CreatedDate
    private Instant createdAt;
    private Instant updatedAt;

    public enum Mode { SALE, RENT }
    public enum Status { PENDING, PAID, HELD, RELEASED, REFUNDED, FAILED }
}
