package com.trustify.dto;

import lombok.Data;

@Data
public class ResolveDisputeRequest {
    // boolean refundBuyer;  // true = refund, false = release full
    // above one changed to this below:
    private String decision; // REFUND_BUYER, RELEASE_SELLER, PARTIAL_DEDUCTION

    private Long deductionCents;  // optional, partial deduction from seller
    private String adminNote;
}
