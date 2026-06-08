package com.trustify.service;

import com.trustify.dto.UserRequestDTO;
import com.trustify.dto.UserResponseDTO;
import com.trustify.exception.ResourceAlreadyExistsException;
import com.trustify.exception.ResourceNotFoundException;
import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public UserResponseDTO createUser(UserRequestDTO dto) {
        if(userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ResourceAlreadyExistsException("User with email " + dto.getEmail() + " already exists");
        }
        if(userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new ResourceAlreadyExistsException("User with username " + dto.getUsername() + " already exists");
        }
        User user = User.builder()
                .email(dto.getEmail())
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword())) // In real applications, ensure to hash passwords before storing
                .phone(dto.getPhone())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);
        return convertToResponse(user);
    }

    // ✅ Fetch single user by ID
    public UserResponseDTO getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

        return convertToResponse(user);
    }

    // ✅ Update password for the currently authenticated user
    public void updatePassword(String email, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private UserResponseDTO convertToResponse(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .build();
    }

}
