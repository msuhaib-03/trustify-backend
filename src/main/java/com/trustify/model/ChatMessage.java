package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "chatMessages")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    @Id
    private String id;
    private String senderId;
    private String receiverId;
    private String listingId; // optional — link to a listing

    private String content;
    private Instant timestamp;
}
