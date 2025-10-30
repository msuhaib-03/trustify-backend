package com.trustify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserResponseDTO {
    private String id;
    private String email;
    private String username;
    private String phone;
    private String role;
    private String status;
}
