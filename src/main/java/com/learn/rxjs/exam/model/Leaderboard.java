package com.learn.rxjs.exam.model;

import java.time.Instant;
import java.util.List;

public record Leaderboard(
        String examId,
        Instant generatedAt,
        int totalStudents,
        int submittedCount,
        int inProgressCount,
        int notStartedCount,
        List<LeaderboardEntry> entries) {
}
