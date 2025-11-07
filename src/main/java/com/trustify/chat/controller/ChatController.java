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



// all the controllers need authentication and authorization mechanism to ensure that only authorized users can access their chats and messages. This can be implemented using JWT tokens, OAuth2, or any other suitable method based on the application's requirements.
// therefore bearer token containing userId is expected in the Authorization header of each request.
// copy & paste bearer token utility from auth service ... api/auth/login

// how to hit the web socket endpoints from postman:
// 40{"token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyYWJpeWE3QGdtYWlsLmNvbSIsImlhdCI6MTc2MjUyODI2NywiZXhwIjoxNzYyNTMxODY3fQ.F7Jwhg1DPF6hIx9bbBUigkw_uUCSwawvd6bAViauR3g"}
    //    42["joinRoom","690dcedfee650736609046cd"]

     //   42["sendMessage", {"chatId":"690dcedfee650736609046cd","message":"Hello from Postman"}]
// ws://localhost:3001/socket.io/?EIO=4&transport=websocket ---> in the ws URL ( websocket )