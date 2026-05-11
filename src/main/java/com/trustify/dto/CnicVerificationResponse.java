package com.trustify.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CnicVerificationResponse {

    private String id;

    private String extractedName;
    private String extractedCnicNumber;

    private String status;

    private String frontImageUrl;
    private String backImageUrl;
}
