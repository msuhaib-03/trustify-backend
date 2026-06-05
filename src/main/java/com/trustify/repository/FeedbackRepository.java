package com.trustify.repository;

import com.trustify.model.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    Page<Feedback> findByUserId(String userId, Pageable pageable);

    Page<Feedback> findByStatus(Feedback.FeedbackStatus status, Pageable pageable);

    long countByStatus(Feedback.FeedbackStatus status);
}
