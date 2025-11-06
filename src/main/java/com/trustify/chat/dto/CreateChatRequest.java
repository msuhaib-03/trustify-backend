package com.trustify.chat.dto;

import lombok.Data;

@Data
public class CreateChatRequest {
    private String buyerId;
    private String sellerId;
}
