package com.trustify.service;

import com.trustify.model.TimelineLog;
import com.trustify.repository.TimelineLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TimelineLogService {
        // This service can be expanded to include methods for logging timeline events,
        // retrieving logs, and analyzing user activity patterns.

    @Autowired
    private TimelineLogRepository timelineLogRepository;

    public void log(String transactionId, String userId, String username,
                    String description, TimelineLog.ActionType actionType, TimelineLog.ActorType actorType) {

        if(username == null || username.isEmpty()){
            username = resolveFallbackUsername(userId);
        }

        TimelineLog log = TimelineLog.builder()
                .transactionId(transactionId)
                .userId(userId)
                .username(username)
                .description(description)
                .actionType(actionType)
                .actorType(actorType)
                .createdAt(Instant.now())
                .build();

        timelineLogRepository.save(log);
    }

    private String resolveFallbackUsername(String userId){
        // In a real implementation, this would query the UserRepository to get the username
        // based on the userId. For now, we can return a placeholder or the userId itself.
        if(userId == null) return "SYSTEM";
        if (userId.equalsIgnoreCase("ADMIN")) return "ADMIN";

        return "USER"; // safe fallback
    }
}

