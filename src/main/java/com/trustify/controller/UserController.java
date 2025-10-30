package com.trustify.controller;

import com.trustify.dto.UserRequestDTO;
import com.trustify.dto.UserResponseDTO;
import com.trustify.model.User;
import com.trustify.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    UserService userService;

    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO dto) {
        return userService.createUser(dto);
    }
}
