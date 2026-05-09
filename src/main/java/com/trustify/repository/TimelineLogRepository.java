package com.trustify.repository;

import com.trustify.model.TimelineLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TimelineLogRepository extends MongoRepository<TimelineLog, String> {
    List<TimelineLog> findByTransactionIdOrderByCreatedAtAsc(String transactionId);
}
