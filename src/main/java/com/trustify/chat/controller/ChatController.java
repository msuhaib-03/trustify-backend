package com.trustify.chat.controller;

import com.trustify.chat.dto.CreateChatRequest;
import com.trustify.chat.dto.StoreMessageRequest;
import com.trustify.chat.model.Chat;
import com.trustify.chat.service.ChatService;
import com.trustify.chat.util.ChatTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatTokenUtil chatTokenUtil;

    @PostMapping("/create")
    public Object createChat(@RequestBody CreateChatRequest req) {

        Chat chat = chatService.createOrGetChat(req.getBuyerId(), req.getSellerId());
        String token = chatTokenUtil.generateChatToken(req.getBuyerId());

        return new Object() {
            public String chatId = chat.getId();
            public String chatToken = token;
        };
    }

    @PostMapping("/store")
    public Chat storeMessage(@RequestBody StoreMessageRequest req) {
        return chatService.saveMessage(req.getChatId(), req.getSenderId(), req.getMessage());
    }

    @GetMapping("/{chatId}")
    public Chat getChat(@PathVariable String chatId) {
        return chatService.getChat(chatId);
    }
}
