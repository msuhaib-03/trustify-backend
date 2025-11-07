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
import java.util.List;
import java.util.Optional;


public interface ChatService {

    Chat createChatIfNotExists(String chatId, List<String> participants);
    Chat saveMessage(String chatId, Message message);
    Page<Chat> getChatsForUser(String userId, Pageable pageable);
    Optional<Chat> getChatById(String chatId);
    List<Chat> getChatsForUser(String userId);
    Chat markMessagesRead(String chatId, String userId);
    List<ChatSummaryDTO> getChatSummaries(String userId, Pageable pageable);
}
