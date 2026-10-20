package com.learn.rxjs.exam.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable, in-memory record of one student's attempt. All access goes through ExamSessionService under a lock. */
public class ExamSession {

    private final String sessionId;
    private final String examId;
    private final String studentId;
    private final Instant startedAt;
    private final int durationSeconds;

    private SessionStatus status = SessionStatus.IN_PROGRESS;
    private final Map<String, String> answers = new LinkedHashMap<>();
    private final Set<String> flaggedQuestionIds = new LinkedHashSet<>();
    private int currentQuestionIndex;
    private final List<TabSwitchEvent> tabSwitchEvents = new ArrayList<>();
    private int saveCount;
    private Instant lastSavedAt;
    private Instant warningShownAt;
    private int submitRequestCount;
    private SubmitResult result;

    public ExamSession(String sessionId, String examId, String studentId, Instant startedAt, int durationSeconds) {
        this.sessionId = sessionId;
        this.examId = examId;
        this.studentId = studentId;
        this.startedAt = startedAt;
        this.durationSeconds = durationSeconds;
    }

    public Instant endsAt() {
        return startedAt.plusSeconds(durationSeconds);
    }

    public String getSessionId() { return sessionId; }
    public String getExamId() { return examId; }
    public String getStudentId() { return studentId; }
    public Instant getStartedAt() { return startedAt; }
    public int getDurationSeconds() { return durationSeconds; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Map<String, String> getAnswers() { return answers; }
    public Set<String> getFlaggedQuestionIds() { return flaggedQuestionIds; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }
    public List<TabSwitchEvent> getTabSwitchEvents() { return tabSwitchEvents; }
    public int getSaveCount() { return saveCount; }
    public void incrementSaveCount() { saveCount++; }
    public Instant getLastSavedAt() { return lastSavedAt; }
    public void setLastSavedAt(Instant lastSavedAt) { this.lastSavedAt = lastSavedAt; }
    public Instant getWarningShownAt() { return warningShownAt; }
    public void setWarningShownAt(Instant warningShownAt) { this.warningShownAt = warningShownAt; }
    public int getSubmitRequestCount() { return submitRequestCount; }
    public void incrementSubmitRequestCount() { submitRequestCount++; }
    public SubmitResult getResult() { return result; }
    public void setResult(SubmitResult result) { this.result = result; }
}
