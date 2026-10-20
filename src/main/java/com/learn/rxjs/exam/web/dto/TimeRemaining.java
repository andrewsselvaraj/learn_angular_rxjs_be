package com.learn.rxjs.exam.web.dto;

import java.time.Instant;

/** Server-authoritative clock, so the countdown can resync after a refresh or a sleeping laptop. */
public record TimeRemaining(
        String sessionId,
        Instant serverTime,
        Instant startedAt,
        Instant endsAt,
        int durationSeconds,
        long remainingSeconds,
        int warningAtSeconds,
        boolean warningDue,
        boolean expired) {
}
