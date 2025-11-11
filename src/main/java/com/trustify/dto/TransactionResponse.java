package com.trustify.dto;

import lombok.Data;

@Data
public class TransactionResponse {
    private String transactionId;
    private String stripeClientSecret; // for frontend to finish payment if needed
    private String stripePaymentIntentId;
}
