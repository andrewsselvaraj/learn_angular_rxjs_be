package com.learn.rxjs.exam.web.dto;

import java.time.Instant;

/** Returned when an attempt starts. Use durationSeconds / remainingSeconds to seed timer(0, 1000). */
public record SessionStarted(
        String sessionId,
        String examId,
        String studentId,
        Instant serverTime,
        Instant startedAt,
        Instant endsAt,
        int durationSeconds,
        long remainingSeconds,
        boolean resumed) {
}
