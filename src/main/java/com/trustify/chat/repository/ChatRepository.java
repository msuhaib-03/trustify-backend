package com.trustify.chat.repository;

import com.trustify.chat.model.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends MongoRepository<Chat, String> {

//    Optional<Chat> findByUser1IdAndUser2Id(String user1Id, String user2Id);
//
//    List<Chat> findByUser1IdOrUser2Id(String userId1, String userId2);

    // find chats where a user is in participants
    @Query("{ 'participants': ?0 }")
    List<Chat> findByParticipant(String userId);

    // optionally with paging
    @Query("{ 'participants': ?0 }")
    Page<Chat> findByParticipant(String userId, Pageable pageable);

}
