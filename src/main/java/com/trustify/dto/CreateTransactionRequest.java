package com.trustify.dto;

import com.trustify.model.Transaction;
import lombok.Data;

@Data
public class CreateTransactionRequest {
    private String listingId;
    private String buyerId;    // email or userId
    private String sellerId;
    private Transaction.TransactionType type;
    private long amountCents;  // integer cents
    private long depositCents; // for rent; optional
    private String currency; // Optional
}
