package com.learn.rxjs.exam.model;

import java.time.Instant;

public record SubmitResult(
        int score,
        int totalMarks,
        double percentage,
        boolean passed,
        int correct,
        int wrong,
        int unanswered,
        String reason,
        Instant submittedAt,
        long timeTakenSeconds) {
}
