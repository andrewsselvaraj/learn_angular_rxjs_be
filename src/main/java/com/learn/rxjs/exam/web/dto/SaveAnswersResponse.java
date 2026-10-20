package com.learn.rxjs.exam.web.dto;

import java.time.Instant;
import java.util.Map;

public record SaveAnswersResponse(
        String sessionId,
        boolean saved,
        Instant savedAt,
        int saveCount,
        int answeredCount,
        int totalQuestions,
        Map<String, String> answers) {
}
