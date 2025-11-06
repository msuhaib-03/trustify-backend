package com.trustify.repository;

import com.trustify.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findBySenderIdAndReceiverIdOrReceiverIdAndSenderId(
            String senderId, String receiverId, String receiverId2, String senderId2);
}
