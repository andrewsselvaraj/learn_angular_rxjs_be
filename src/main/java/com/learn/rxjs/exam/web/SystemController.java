package com.learn.rxjs.exam.web;

import com.learn.rxjs.exam.service.ChaosService;
import com.learn.rxjs.exam.service.ExamSessionService;
import com.learn.rxjs.exam.service.LeaderboardService;
import com.learn.rxjs.exam.web.dto.ChaosConfig;
import com.learn.rxjs.exam.web.dto.Ping;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connectivity check, network-failure switches and a reset for the in-memory data. */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final ChaosService chaos;
    private final ExamSessionService sessions;
    private final LeaderboardService leaderboard;

    public SystemController(ChaosService chaos, ExamSessionService sessions, LeaderboardService leaderboard) {
        this.chaos = chaos;
        this.sessions = sessions;
        this.leaderboard = leaderboard;
    }

    /** Lightweight call to confirm the server is reachable again after window 'online'. */
    @GetMapping("/health/ping")
    public Ping ping() {
        return new Ping("UP", Instant.now());
    }

    @GetMapping("/chaos")
    public ChaosConfig chaos() {
        return chaos.current();
    }

    @PutMapping("/chaos")
    public ChaosConfig updateChaos(@RequestBody ChaosConfig config) {
        return chaos.update(config);
    }

    @DeleteMapping("/chaos")
    public ChaosConfig clearChaos() {
        chaos.reset();
        return chaos.current();
    }

    /** Clears every session, re-seeds the leaderboard and turns off the chaos switches. */
    @PostMapping("/admin/reset")
    public Map<String, Object> reset() {
        sessions.reset();
        leaderboard.reset();
        chaos.reset();
        return Map.of("reset", true, "at", Instant.now());
    }
}
