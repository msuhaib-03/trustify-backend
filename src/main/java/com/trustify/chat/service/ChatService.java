package com.trustify.chat.service;

import com.trustify.chat.model.Chat;
import com.trustify.chat.model.Message;
import com.trustify.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;

    public Chat createOrGetChat(String user1, String user2) {

        Optional<Chat> existing = chatRepository.findByUser1IdAndUser2Id(user1, user2);
        if (existing.isPresent()) return existing.get();

        Chat chat = new Chat();
        chat.setUser1Id(user1);
        chat.setUser2Id(user2);

        return chatRepository.save(chat);
    }

    public Chat saveMessage(String chatId, String senderId, String messageText) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        Message msg = new Message();
        msg.setSenderId(senderId);
        msg.setContent(messageText);     // match the field name above
        msg.setTimestamp(Instant.now());

        chat.getMessages().add(msg);
        return chatRepository.save(chat);
    }

    public Chat getChat(String chatId) {
        return chatRepository.findById(chatId).orElseThrow();
    }
}
