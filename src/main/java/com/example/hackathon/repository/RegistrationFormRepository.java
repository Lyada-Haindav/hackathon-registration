package com.example.hackathon.repository;

import com.example.hackathon.model.RegistrationForm;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RegistrationFormRepository extends MongoRepository<RegistrationForm, String> {
    Optional<RegistrationForm> findByEventId(String eventId);

    void deleteByEventId(String eventId);
}
