package com.trustify.dto;

import com.trustify.model.Transaction;
import lombok.Data;

@Data
public class CreateTransactionRequest {
    private String listingId;
    private String buyerId;    // email or userId
    private String sellerId;
    private Transaction.TransactionType type;
    private long amountCents;  // integer cents (required) — already multiplied by duration on frontend
    private Long depositCents; // for rent (nullable -null/0 means no deposit (sales))
    private String currency; // Optional

    // Rental duration fields — only relevant when type == RENT
    private Integer rentalDurationUnits; // number of hours or days the buyer wants
    private String rentalPeriod;         // "PER_HOUR" or "PER_DAY" — mirrors Listing.rentalPeriod
}
