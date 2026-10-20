package com.learn.rxjs.exam.model;

import java.time.Instant;

/** One row on the teacher's live results screen. rank is null until the student has submitted. */
public record LeaderboardEntry(
        Integer rank,
        String studentId,
        String name,
        String avatarUrl,
        SessionStatus status,
        Integer score,
        Double percentage,
        Instant submittedAt,
        Long timeTakenSeconds) {
}
