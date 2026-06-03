package com.trustify.dto;

import com.trustify.model.Transaction;
import lombok.Data;

@Data
public class CreateTransactionRequest {
    private String listingId;
    private String buyerId;    // email or userId
    private String sellerId;
    private Transaction.TransactionType type;
    private long amountCents;  // integer cents (required)
    private Long depositCents; // for rent (nullable -null/0 means no deposit (sales))
    private String currency; // Optional
}
