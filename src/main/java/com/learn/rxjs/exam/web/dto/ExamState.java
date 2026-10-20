package com.learn.rxjs.exam.web.dto;

import com.learn.rxjs.exam.model.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The shape intended for BehaviorSubject&lt;ExamState&gt; in the Angular ExamService. */
public record ExamState(
        String sessionId,
        String examId,
        String studentId,
        SessionStatus status,
        int currentQuestionIndex,
        int totalQuestions,
        int answeredCount,
        Map<String, String> answers,
        List<String> flaggedQuestionIds,
        long remainingSeconds,
        int tabSwitchCount,
        Instant lastSavedAt) {
}
