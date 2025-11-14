package com.trustify.dto;

import lombok.Data;

@Data
public class ResolveDisputeRequest {
    private boolean refundBuyer;  // true = refund, false = release full
    private Long deductionCents;  // optional, partial deduction from seller
    private String adminNote;
}
