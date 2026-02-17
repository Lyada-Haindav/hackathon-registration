package com.example.hackathon.service;

import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.UserRepository;
import com.example.hackathon.util.SecurityUtil;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = SecurityUtil.currentUsername();
        if (email == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return findByEmail(email);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));
    }
}
