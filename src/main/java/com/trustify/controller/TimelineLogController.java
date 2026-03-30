package com.trustify.controller;

import com.trustify.dto.TimelineLogDTO;
import com.trustify.model.TimelineLog;
import com.trustify.repository.TimelineLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/timeline")
public class TimelineLogController {
    // This controller will handle endpoints related to timeline logs, such as fetching logs for a specific user or event.

    @Autowired
    private TimelineLogRepository timelineLogRepository;

    @GetMapping("/{transactionId}")
    public List<TimelineLogDTO> getTimeline(@PathVariable String transactionId){
        List<TimelineLog> logs = timelineLogRepository
                .findByTransactionIdOrderByCreatedAtAsc(transactionId);

        List<TimelineLogDTO> collect = logs.stream()
                .map(log -> TimelineLogDTO.builder()
                .action(log.getActionType().name())
                .description(log.getDescription())
                .username(log.getUsername())
                .timestamp(log.getCreatedAt())
                .build()).collect(Collectors.toList());

        return collect;
    }

}
