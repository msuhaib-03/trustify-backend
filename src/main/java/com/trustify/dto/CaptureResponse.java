package com.trustify.dto;

import lombok.Data;

@Data
public class CaptureResponse {
    private String transactionId;
    private String stripePaymentIntentId;
    private String stripeChargeId;
    private String status;

    public CaptureResponse(String id, String id1, String chargeId, String name) {
        this.transactionId = id;
        this.stripePaymentIntentId = id1;
        this.stripeChargeId = chargeId;
        this.status = name;
    }
}
