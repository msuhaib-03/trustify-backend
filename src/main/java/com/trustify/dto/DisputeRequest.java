package com.trustify.dto;

import lombok.Data;

import java.util.List;

@Data
public class DisputeRequest {
    private String reason;
    private List<String> evidenceUrls; // images or documents uploaded
}
