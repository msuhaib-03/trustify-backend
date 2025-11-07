package com.trustify.chat.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ChatSummaryDTO {
    private String chatId;
    private String title; // optional (other participant name)
    private String lastMessage;
    private Instant lastTimestamp;
    private long unreadCount;
}
