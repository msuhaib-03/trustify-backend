package com.trustify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrustProfileResponse {
    private String userId;
    private String email;
    private String username;
    private int fraudScore;
    private double trustRating;
    private String riskLevel;    // SAFE | MEDIUM | HIGH | CRITICAL
    private String trustBadge;   // TRUSTED | NORMAL | RISKY
    private boolean verified;
    private int disputeCount;
    private int totalTransactions;
    private int successfulTransactions;
}
