package com.trustify.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TimelineLogDTO {
    private String action;
    private String description;
    private String username;
    private Instant timestamp;
}
