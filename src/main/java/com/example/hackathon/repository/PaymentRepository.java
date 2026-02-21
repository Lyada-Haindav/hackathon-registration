package com.example.hackathon.repository;

import com.example.hackathon.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    List<Payment> findByTeamId(String teamId);

    Optional<Payment> findTopByTeamIdOrderByCreatedAtDesc(String teamId);

    List<Payment> findByEventId(String eventId);

    void deleteByTeamId(String teamId);
}
