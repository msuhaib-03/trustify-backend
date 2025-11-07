package com.trustify.chat.controller;

import com.trustify.chat.dto.CreateChatRequest;
import com.trustify.chat.dto.StoreMessageRequest;
import com.trustify.chat.model.Chat;
import com.trustify.chat.model.Message;
import com.trustify.chat.service.ChatService;
import com.trustify.chat.util.ChatTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatTokenUtil chatTokenUtil;

    // Create chat (or return existing)
    @PostMapping
    public ResponseEntity<?> createChat(@RequestBody Map<String, Object> body) {
        String chatId = (String) body.get("chatId"); // optional
        List<String> participants = (List<String>) body.get("participants");
        Chat chat = chatService.createChatIfNotExists(chatId, participants);
        return ResponseEntity.ok(chat);
    }

    // Save message (Node will POST here)
    @PostMapping("/{chatId}/messages")
    public ResponseEntity<?> postMessage(@PathVariable String chatId, @RequestBody Message dto) {
        Chat saved = chatService.saveMessage(chatId, dto);
        return ResponseEntity.ok(saved);
    }

    // Get chat history (messages)
    @GetMapping("/{chatId}/messages")
    public ResponseEntity<?> getHistory(@PathVariable String chatId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        var chatOpt = chatService.getChatById(chatId);
        if (chatOpt.isEmpty()) return ResponseEntity.notFound().build();
        Chat chat = chatOpt.get();
        // naive pagination on messages:
        int from = Math.max(0, page * size);
        int to = Math.min(chat.getMessages().size(), from + size);
        var sub = chat.getMessages().subList(from, to);
        return ResponseEntity.ok(Map.of(
                "chatId", chat.getId(),
                "messages", sub,
                "totalMessages", chat.getMessages().size()
        ));
    }

    // Chat list for a user
    @GetMapping
    public ResponseEntity<?> getChatsForUser(@RequestParam String userId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Page<Chat> p = chatService.getChatsForUser(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(p);
    }

    @PostMapping("/{chatId}/read")
    public ResponseEntity<?> markRead(@PathVariable String chatId,
                                      @RequestParam String userId) {
        Chat updated = chatService.markMessagesRead(chatId, userId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/summaries")
    public ResponseEntity<?> getSummaries(@RequestParam String userId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        var list = chatService.getChatSummaries(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(list);
    }


}
