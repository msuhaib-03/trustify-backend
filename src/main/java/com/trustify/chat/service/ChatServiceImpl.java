package com.trustify.chat.service;

import com.trustify.chat.dto.ChatSummaryDTO;
import com.trustify.chat.model.Chat;
import com.trustify.chat.model.Message;
import com.trustify.chat.repository.ChatRepository;
import com.trustify.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Override
    public Chat createChatIfNotExists(String chatId, List<String> participants) {
        // 1. If caller supplied an explicit chatId, return that chat if it exists
        if (chatId != null && chatRepository.existsById(chatId)) {
            return chatRepository.findById(chatId).get();
        }
        // 2. If we have exactly two participants, check for an existing 1-on-1 chat
        if (participants != null && participants.size() == 2) {
            Optional<Chat> existing = chatRepository.findByBothParticipants(
                    participants.get(0), participants.get(1));
            if (existing.isPresent()) return existing.get();
        }
        // 3. Create a chat
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

    /** Resolves a userId (email OR objectId) to both forms for $in queries */
    private List<String> resolveUserIds(String userId) {
        List<String> ids = new ArrayList<>();
        ids.add(userId);
        try {
            // If it looks like an email, also search by the user's ObjectId
            userRepository.findByEmail(userId).ifPresent(u -> {
                if (u.getId() != null && !u.getId().equals(userId)) ids.add(u.getId());
            });
            // If it looks like an ObjectId, also search by the user's email
            userRepository.findById(userId).ifPresent(u -> {
                if (u.getEmail() != null && !u.getEmail().equals(userId)) ids.add(u.getEmail());
            });
        } catch (Exception ignored) {}
        return ids;
    }

    public List<ChatSummaryDTO> getChatSummaries(String userId, Pageable p) {
        List<String> ids = resolveUserIds(userId);
        Page<Chat> page = chatRepository.findByParticipantIn(ids, p);
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
        List<String> ids = resolveUserIds(userId);
        return chatRepository.findByParticipantIn(ids, pageable);
    }

    @Override
    public Optional<Chat> getChatById(String chatId) {
        return chatRepository.findById(chatId);
    }

    @Override
    public List<Chat> getChatsForUser(String userId) {
        List<String> ids = resolveUserIds(userId);
        return chatRepository.findByParticipantIn(ids);
    }
}
