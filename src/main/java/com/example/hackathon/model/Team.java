package com.example.hackathon.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "teams")
@CompoundIndex(name = "event_user_unique", def = "{'eventId': 1, 'userId': 1}", unique = true)
public class Team {

    @Id
    private String id;

    private String eventId;

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String teamName;

    private int teamSize;

    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private String paymentRecordId;

    private Map<String, Object> formResponses = new HashMap<>();

    private String selectedProblemStatementId;

    private String selectedProblemStatementTitle;

    private double totalScore = 0.0;

    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getPaymentRecordId() {
        return paymentRecordId;
    }

    public void setPaymentRecordId(String paymentRecordId) {
        this.paymentRecordId = paymentRecordId;
    }

    public Map<String, Object> getFormResponses() {
        return formResponses;
    }

    public void setFormResponses(Map<String, Object> formResponses) {
        this.formResponses = formResponses;
    }

    public String getSelectedProblemStatementId() {
        return selectedProblemStatementId;
    }

    public void setSelectedProblemStatementId(String selectedProblemStatementId) {
        this.selectedProblemStatementId = selectedProblemStatementId;
    }

    public String getSelectedProblemStatementTitle() {
        return selectedProblemStatementTitle;
    }

    public void setSelectedProblemStatementTitle(String selectedProblemStatementTitle) {
        this.selectedProblemStatementTitle = selectedProblemStatementTitle;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
