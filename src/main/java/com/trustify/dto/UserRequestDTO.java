package com.trustify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequestDTO {

    @NotBlank(message = "Email is mandatory")
    @Email(message = "Invalid Email Address")
    private String email;

    @NotBlank(message = "Username is mandatory")
    private String username;

    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    @NotBlank(message = "Phone number is mandatory")
    private String phone;
}
