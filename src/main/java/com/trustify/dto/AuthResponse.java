package com.trustify.dto;

import com.trustify.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String username;
    private String email;
    private String token;
    private User.Role role;
}
