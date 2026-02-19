package com.example.hackathon.security;

import com.example.hackathon.model.User;
import com.example.hackathon.model.Role;
import com.example.hackathon.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final boolean userEmailVerificationRequired;

    public CustomUserDetailsService(UserService userService,
                                    @Value("${app.auth.user-email-verification-required:true}") boolean userEmailVerificationRequired) {
        this.userService = userService;
        this.userEmailVerificationRequired = userEmailVerificationRequired;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user;
        try {
            user = userService.findByEmail(username);
        } catch (Exception ex) {
            throw new UsernameNotFoundException("User not found");
        }

        boolean verificationGateEnabled = userEmailVerificationRequired;

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isActive() && (user.getRole() == Role.FACULTY || !verificationGateEnabled || user.isEmailVerified()),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
