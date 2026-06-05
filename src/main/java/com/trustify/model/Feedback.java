package com.trustify.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "feedback")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Feedback {


    @Id
    private String feedbackId;

    private String userId;
    private String userName;
    private String userEmail;

    private FeedbackType feedbackType;

    /** Optional 1–5 star rating; null when not provided */
    private Integer rating;

    private String title;
    private String message;

    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum FeedbackType {
        BUG_REPORT, FEATURE_REQUEST, PAYMENT_ISSUE, CHAT_ISSUE, OTHER
    }

    public enum FeedbackStatus {
        NEW, REVIEWED, RESOLVED
    }

}
