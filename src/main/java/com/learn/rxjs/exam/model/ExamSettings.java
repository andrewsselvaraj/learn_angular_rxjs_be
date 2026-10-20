package com.learn.rxjs.exam.model;

/**
 * Everything the client needs to drive the exam: the timer duration, the "5 minutes left" warning,
 * the auto-save debounce, the retry policy and the tab-switch limit.
 */
public record ExamSettings(
        String examId,
        String title,
        String subject,
        int durationSeconds,
        int totalQuestions,
        int totalMarks,
        int passPercentage,
        int warningAtSeconds,
        int autoSaveDebounceMs,
        int saveRetryCount,
        int saveRetryDelayMs,
        int maxTabSwitches,
        int leaderboardPollIntervalMs,
        boolean allowReview) {
}
