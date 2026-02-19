package com.example.hackathon.controller;

import com.example.hackathon.dto.EventResponse;
import com.example.hackathon.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final EventService eventService;

    public PublicController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> listEventsForHome() {
        List<EventResponse> events = eventService.getActiveEvents().stream()
                .sorted(Comparator
                        .comparing(EventResponse::startDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EventResponse::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return ResponseEntity.ok(events);
    }
}
