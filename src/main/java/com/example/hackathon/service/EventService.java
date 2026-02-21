package com.example.hackathon.service;

import com.example.hackathon.dto.CreateEventRequest;
import com.example.hackathon.dto.EventResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.HackathonEvent;
import com.example.hackathon.model.Team;
import com.example.hackathon.repository.CriterionRepository;
import com.example.hackathon.repository.EvaluationRepository;
import com.example.hackathon.repository.HackathonEventRepository;
import com.example.hackathon.repository.PaymentRepository;
import com.example.hackathon.repository.ProblemStatementRepository;
import com.example.hackathon.repository.RegistrationFormRepository;
import com.example.hackathon.repository.TeamMemberRepository;
import com.example.hackathon.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class EventService {

    private final HackathonEventRepository eventRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PaymentRepository paymentRepository;
    private final EvaluationRepository evaluationRepository;
    private final CriterionRepository criterionRepository;
    private final ProblemStatementRepository problemStatementRepository;
    private final RegistrationFormRepository formRepository;
    private final int feeSplitMembers;
    private final long activeEventsCacheTtlMs;
    private volatile List<EventResponse> activeEventsCache = List.of();
    private volatile long activeEventsCachedAtMs = 0L;
    private volatile boolean activeEventsCacheInitialized = false;

    public EventService(HackathonEventRepository eventRepository,
                        TeamRepository teamRepository,
                        TeamMemberRepository teamMemberRepository,
                        PaymentRepository paymentRepository,
                        EvaluationRepository evaluationRepository,
                        CriterionRepository criterionRepository,
                        ProblemStatementRepository problemStatementRepository,
                        RegistrationFormRepository formRepository,
                        @Value("${app.payment.fee-split-members:4}") int feeSplitMembers,
                        @Value("${app.cache.active-events-ttl-ms:15000}") long activeEventsCacheTtlMs) {
        this.eventRepository = eventRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.paymentRepository = paymentRepository;
        this.evaluationRepository = evaluationRepository;
        this.criterionRepository = criterionRepository;
        this.problemStatementRepository = problemStatementRepository;
        this.formRepository = formRepository;
        this.feeSplitMembers = feeSplitMembers;
        this.activeEventsCacheTtlMs = Math.max(activeEventsCacheTtlMs, 1000L);
    }

    public EventResponse createEvent(CreateEventRequest request, String facultyEmail) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("Event end date cannot be before start date");
        }
        if (request.registrationCloseDate().isBefore(request.registrationOpenDate())) {
            throw new BadRequestException("Registration close date cannot be before open date");
        }

        HackathonEvent event = new HackathonEvent();
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setAboutEvent(normalizeText(request.aboutEvent()));
        event.setPosterUrl(normalizeText(request.posterUrl()));
        event.setTelegramGroupLink(normalizeTelegramLink(request.telegramGroupLink()));
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setRegistrationOpenDate(request.registrationOpenDate());
        event.setRegistrationCloseDate(request.registrationCloseDate());
        event.setRegistrationOpen(false);
        event.setRegistrationFee(request.registrationFee());
        event.setActive(request.active());
        event.setOnHold(false);
        event.setCreatedBy(facultyEmail);

        HackathonEvent saved = eventRepository.save(event);
        invalidateCaches();
        return toResponse(saved);
    }

    public EventResponse updateEvent(String eventId, CreateEventRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("Event end date cannot be before start date");
        }
        if (request.registrationCloseDate().isBefore(request.registrationOpenDate())) {
            throw new BadRequestException("Registration close date cannot be before open date");
        }

        HackathonEvent event = getEventEntity(eventId);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setAboutEvent(normalizeText(request.aboutEvent()));
        event.setPosterUrl(normalizeText(request.posterUrl()));
        event.setTelegramGroupLink(normalizeTelegramLink(request.telegramGroupLink()));
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setRegistrationOpenDate(request.registrationOpenDate());
        event.setRegistrationCloseDate(request.registrationCloseDate());
        event.setRegistrationFee(request.registrationFee());
        event.setActive(request.active());
        if (request.active()) {
            event.setOnHold(false);
        }
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public List<EventResponse> getActiveEvents() {
        long now = System.currentTimeMillis();
        if (activeEventsCacheInitialized && now - activeEventsCachedAtMs <= activeEventsCacheTtlMs) {
            return activeEventsCache;
        }

        List<EventResponse> fresh = eventRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
        activeEventsCache = List.copyOf(fresh);
        activeEventsCachedAtMs = now;
        activeEventsCacheInitialized = true;
        return fresh;
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream().map(this::toResponse).toList();
    }

    public EventResponse getEvent(String eventId) {
        return toResponse(getEventEntity(eventId));
    }

    public EventResponse setLeaderboardVisibility(String eventId, boolean visible) {
        HackathonEvent event = getEventEntity(eventId);
        event.setLeaderboardVisible(visible);
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public EventResponse holdEvent(String eventId) {
        HackathonEvent event = getEventEntity(eventId);
        event.setOnHold(true);
        event.setActive(false);
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public EventResponse resumeEvent(String eventId) {
        HackathonEvent event = getEventEntity(eventId);
        event.setOnHold(false);
        event.setActive(true);
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public EventResponse openRegistrationNow(String eventId) {
        HackathonEvent event = getEventEntity(eventId);
        event.setRegistrationOpen(true);
        event.setOnHold(false);
        event.setActive(true);
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public EventResponse closeRegistrationNow(String eventId) {
        HackathonEvent event = getEventEntity(eventId);
        event.setRegistrationOpen(false);
        EventResponse response = toResponse(eventRepository.save(event));
        invalidateCaches();
        return response;
    }

    public void deleteEvent(String eventId) {
        getEventEntity(eventId);

        List<Team> teams = teamRepository.findByEventId(eventId);
        for (Team team : teams) {
            teamMemberRepository.deleteByTeamId(team.getId());
            paymentRepository.deleteByTeamId(team.getId());
        }

        evaluationRepository.deleteByEventId(eventId);
        criterionRepository.deleteByEventId(eventId);
        problemStatementRepository.deleteByEventId(eventId);
        formRepository.deleteByEventId(eventId);
        teamRepository.deleteByEventId(eventId);
        eventRepository.deleteById(eventId);
        invalidateCaches();
    }

    public HackathonEvent getEventEntity(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    public void validateRegistrationWindow(HackathonEvent event) {
        if (!event.isActive() || event.isOnHold()) {
            throw new BadRequestException("Registrations are currently paused for this event");
        }

        if (!event.isRegistrationOpen()) {
            throw new BadRequestException("Registration is currently closed for this event. Wait for organizer to open registrations.");
        }
    }

    private EventResponse toResponse(HackathonEvent event) {
        int splitMembers = feeSplitMembers <= 0 ? 1 : feeSplitMembers;
        BigDecimal registrationFee = event.getRegistrationFee() == null ? BigDecimal.ZERO : event.getRegistrationFee();
        BigDecimal feePerMember = registrationFee.divide(BigDecimal.valueOf(splitMembers), 2, RoundingMode.HALF_UP);

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getAboutEvent(),
                event.getPosterUrl(),
                event.getTelegramGroupLink(),
                event.getStartDate(),
                event.getEndDate(),
                event.getRegistrationOpenDate(),
                event.getRegistrationCloseDate(),
                registrationFee,
                feePerMember,
                splitMembers,
                event.isActive(),
                event.isRegistrationOpen(),
                event.isLeaderboardVisible(),
                event.isOnHold()
        );
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTelegramLink(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            return normalized;
        }
        if (normalized.startsWith("t.me/") || normalized.startsWith("telegram.me/")) {
            return "https://" + normalized;
        }
        return normalized;
    }

    public void invalidateCaches() {
        activeEventsCache = List.of();
        activeEventsCachedAtMs = 0L;
        activeEventsCacheInitialized = false;
    }
}
