package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "disputes")
public class Dispute {

    @Id
    private String id;

    private String transactionId;  // store the transaction _id directly

    private String openedBy;       // buyerId
    private String reason;
    private String evidence;       // optional file URLs or text

    private Instant createdAt;
    private Instant resolvedAt;
    private String resolvedBy;     // adminId
    private String resolutionNote;
    private Long refundAmountCents;  // optional
    private String status;
}
