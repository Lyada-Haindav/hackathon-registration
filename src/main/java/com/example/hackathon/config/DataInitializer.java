package com.example.hackathon.config;

import com.example.hackathon.service.AuthService;
import com.example.hackathon.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner bootstrapFaculty(AuthService authService,
                                       UserService userService,
                                       @Value("${app.admin.email}") String adminEmail,
                                       @Value("${app.admin.password}") String adminPassword) {
        return args -> {
            userService.deduplicateUsersByEmail();
            authService.ensureFacultyUser("Default Faculty", adminEmail, adminPassword);
            userService.deduplicateUsersByEmail();
        };
    }
}
