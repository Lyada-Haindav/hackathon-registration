package com.example.hackathon.controller;

import com.example.hackathon.dto.*;
import com.example.hackathon.model.RegistrationForm;
import com.example.hackathon.service.*;
import com.example.hackathon.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final EventService eventService;
    private final FormService formService;
    private final TeamService teamService;
    private final PaymentService paymentService;
    private final ProblemStatementService problemStatementService;

    public UserController(EventService eventService,
                          FormService formService,
                          TeamService teamService,
                          PaymentService paymentService,
                          ProblemStatementService problemStatementService) {
        this.eventService = eventService;
        this.formService = formService;
        this.teamService = teamService;
        this.paymentService = paymentService;
        this.problemStatementService = problemStatementService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> listEvents() {
        return ResponseEntity.ok(eventService.getActiveEvents());
    }

    @GetMapping("/forms/{eventId}")
    public ResponseEntity<RegistrationForm> getForm(@PathVariable String eventId) {
        return ResponseEntity.ok(formService.getOrCreateFormByEventId(eventId));
    }

    @PostMapping("/teams/register")
    public ResponseEntity<TeamResponse> registerTeam(@Valid @RequestBody TeamRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.registerTeam(SecurityUtil.currentUsername(), request));
    }

    @GetMapping("/teams/{eventId}")
    public ResponseEntity<TeamResponse> getTeamByEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(teamService.getUserTeamByEvent(SecurityUtil.currentUsername(), eventId));
    }

    @PutMapping("/teams/{teamId}/problem-statements/{problemId}")
    public ResponseEntity<TeamResponse> selectProblemStatement(@PathVariable String teamId,
                                                               @PathVariable String problemId) {
        return ResponseEntity.ok(teamService.selectProblemStatement(SecurityUtil.currentUsername(), teamId, problemId));
    }

    @GetMapping("/teams")
    public ResponseEntity<List<TeamResponse>> getMyTeams() {
        return ResponseEntity.ok(teamService.getUserTeams(SecurityUtil.currentUsername()));
    }

    @PostMapping("/payments/{teamId}/order")
    public ResponseEntity<PaymentOrderResponse> createOrder(@PathVariable String teamId) {
        return ResponseEntity.ok(paymentService.createOrder(SecurityUtil.currentUsername(), teamId));
    }

    @PostMapping("/payments/{teamId}/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(@PathVariable String teamId,
                                                                     @Valid @RequestBody PaymentVerificationRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(SecurityUtil.currentUsername(), teamId, request));
    }

    @GetMapping("/problem-statements/{eventId}")
    public ResponseEntity<List<ProblemStatementResponse>> listReleasedProblems(@PathVariable String eventId) {
        return ResponseEntity.ok(problemStatementService.listReleasedForUsers(eventId));
    }
}
