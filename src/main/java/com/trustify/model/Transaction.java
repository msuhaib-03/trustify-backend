package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {
    @Id
    private String id;

    private String listingId;
    private String buyerId;   // could be email or userId
    private String sellerId;
    private String getBuyerUsername;

    private TransactionType type; // SALE or RENT

    private long amountCents;
    private Long depositCents; // for rent
    private Long rentalFeeCents;     // part of amountCents
    private String currency;

    private TransactionStatus status;

    private String stripePaymentIntentId;
    private String stripeChargeId;

    // ------------- Manual release fields -------------
    private Instant releaseRequestedAt;
    private String releaseRequestedBy;
    private String releaseRequestedNote;

    private Long authorizedAmountCents;
    private Long amountCapturedCents;
    private Long platformFeeCents;
    private String sellerStripeAccountId;

    private LocalDate rentalStartDate;
    private LocalDate rentalEndDate;
    private boolean renterPickedUp;
    private boolean renterReturned;

    private Map<String,Object> metadata;

    @CreatedDate
    private Instant createdAt;
    private Instant updatedAt;


    public enum TransactionType { SALE, RENT }

    public enum TransactionStatus {
        PENDING, PENDING_DISPUTE ,AUTHORIZED, HELD,
        PENDING_RELEASE , PARTIALLY_RELEASED ,RELEASED,
        REFUNDED, CANCELLED, FAILED, MANUAL_REVIEW,
        RENTAL_IN_PROGRESS, RENTAL_RETURNED, DAMAGE_RESOLVED,
        COMPLETED
    }
}
