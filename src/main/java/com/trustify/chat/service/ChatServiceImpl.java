package com.trustify.chat.service;

import com.trustify.chat.dto.ChatSummaryDTO;
import com.trustify.chat.model.Chat;
import com.trustify.chat.model.Message;
import com.trustify.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService{

    private final ChatRepository chatRepository;

    @Override
    public Chat createChatIfNotExists(String chatId, List<String> participants) {
        if (chatId != null && chatRepository.existsById(chatId)) {
            return chatRepository.findById(chatId).get();
        }
        Chat chat = Chat.builder()
                .participants(new HashSet<>(participants))
                .messages(new ArrayList<>())
                .updatedAt(Instant.now())
                .build();
        return chatRepository.save(chat);
    }

    @Override
    public Chat saveMessage(String chatId, Message message) {
        Chat chat = chatRepository.findById(chatId).orElseGet(() -> {
            // create chat with sender and unknown receiver if missing
            Chat c = Chat.builder()
                    .participants(new HashSet<>())
                    .messages(new ArrayList<>())
                    .updatedAt(Instant.now())
                    .build();
            return chatRepository.save(c);
        });

        message.setTimestamp(Instant.now());
        if (chat.getMessages() == null) chat.setMessages(new ArrayList<>());
        chat.getMessages().add(message);
        chat.setUpdatedAt(Instant.now());
        return chatRepository.save(chat);
    }

    public Chat markMessagesRead(String chatId, String userId) {
        Chat chat = chatRepository.findById(chatId).orElseThrow();
        chat.getMessages().forEach(m -> m.getReadBy().add(userId));
        return chatRepository.save(chat);
    }

    public List<ChatSummaryDTO> getChatSummaries(String userId, Pageable p) {
        Page<Chat> page = chatRepository.findByParticipant(userId, p);
        return page.stream().map(chat -> {
            Message last = chat.getMessages().isEmpty() ? null : chat.getMessages().get(chat.getMessages().size()-1);
            ChatSummaryDTO dto = new ChatSummaryDTO();
            dto.setChatId(chat.getId());
            dto.setLastMessage(last != null ? last.getContent() : null);
            dto.setLastTimestamp(last != null ? last.getTimestamp() : chat.getUpdatedAt());
            long unread = chat.getMessages().stream().filter(m -> !m.getReadBy().contains(userId)).count();
            dto.setUnreadCount(unread);
            return dto;
        }).collect(Collectors.toList());
    }


    @Override
    public Page<Chat> getChatsForUser(String userId, Pageable pageable) {
        return chatRepository.findByParticipant(userId, pageable);
    }

    @Override
    public Optional<Chat> getChatById(String chatId) {
        return chatRepository.findById(chatId);
    }

    @Override
    public List<Chat> getChatsForUser(String userId) {
        return chatRepository.findByParticipant(userId);
    }
}
