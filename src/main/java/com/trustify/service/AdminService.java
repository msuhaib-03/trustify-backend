package com.trustify.service;

import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    @Autowired
    private UserRepository userRepository;

    public void suspendUser(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(User.AccountStatus.SUSPENDED);
            userRepository.save(user);
        });
    }
}
