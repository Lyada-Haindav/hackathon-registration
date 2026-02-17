package com.example.hackathon.controller;

import com.example.hackathon.dto.LeaderboardEntryResponse;
import com.example.hackathon.service.LeaderboardService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/{eventId}")
    public List<LeaderboardEntryResponse> getLeaderboard(@PathVariable String eventId,
                                                         Authentication authentication) {
        return leaderboardService.getLeaderboard(eventId, isFaculty(authentication));
    }

    @GetMapping(value = "/{eventId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLeaderboard(@PathVariable String eventId,
                                        Authentication authentication) {
        final boolean facultyViewer = isFaculty(authentication);
        leaderboardService.getLeaderboard(eventId, facultyViewer);

        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(leaderboardService.getLeaderboard(eventId, facultyViewer));
            } catch (IOException | RuntimeException ex) {
                emitter.completeWithError(ex);
            }
        }, 0, 5, TimeUnit.SECONDS);

        emitter.onCompletion(scheduler::shutdown);
        emitter.onTimeout(() -> {
            emitter.complete();
            scheduler.shutdown();
        });
        emitter.onError(err -> scheduler.shutdown());

        return emitter;
    }

    private boolean isFaculty(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_FACULTY".equals(authority.getAuthority()));
    }
}
