package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "cnic_verifications")
public class CnicVerification {

    @Id
    private String id;

    private String userId;

    private String frontImageUrl;
    private String backImageUrl;

    private String extractedName;
    private String extractedCnicNumber;

    private VerificationStatus status;

    private String adminRemarks; // for admin to provide feedback on rejection or approval

    private LocalTime submittedAt;
    private LocalTime reviewedAt;

    public enum VerificationStatus{
        PENDING,
        APPROVED,
        REJECTED
    }
}
