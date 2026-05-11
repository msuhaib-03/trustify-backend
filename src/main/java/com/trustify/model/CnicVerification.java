package com.trustify.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
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

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    public enum VerificationStatus{
        PENDING,
        APPROVED,
        REJECTED
    }
}
