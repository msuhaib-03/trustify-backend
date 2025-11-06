package com.trustify.chat.model;

import lombok.Data;

import java.time.Instant;

@Data
public class Message {
    private String id;
    private String senderId;
    private String content;
    private Instant timestamp;
}
