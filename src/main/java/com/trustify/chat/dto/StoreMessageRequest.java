package com.trustify.chat.dto;

import lombok.Data;

@Data
public class StoreMessageRequest{
    private String chatId;
    private String senderId;
    private String message;
}
