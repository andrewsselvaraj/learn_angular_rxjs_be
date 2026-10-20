package com.learn.rxjs.exam.service;

import com.learn.rxjs.exam.model.ExamSession;
import com.learn.rxjs.exam.model.ExamSettings;
import com.learn.rxjs.exam.model.Option;
import com.learn.rxjs.exam.model.Question;
import com.learn.rxjs.exam.model.SessionStatus;
import com.learn.rxjs.exam.model.SubmitResult;
import com.learn.rxjs.exam.model.TabSwitchEvent;
import com.learn.rxjs.exam.web.dto.ExamState;
import com.learn.rxjs.exam.web.dto.ExamStatePatch;
import com.learn.rxjs.exam.web.dto.SaveAnswersRequest;
import com.learn.rxjs.exam.web.dto.SaveAnswersResponse;
import com.learn.rxjs.exam.web.dto.SessionStarted;
import com.learn.rxjs.exam.web.dto.SubmitRequest;
import com.learn.rxjs.exam.web.dto.SubmitResponse;
import com.learn.rxjs.exam.web.dto.TabSwitchRequest;
import com.learn.rxjs.exam.web.dto.TabSwitchSummary;
import com.learn.rxjs.exam.web.dto.TimeRemaining;
import com.learn.rxjs.exam.web.dto.WarningAck;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** In-memory exam attempts. Every mutation of a session happens while holding that session's monitor. */
@Service
public class ExamSessionService {

    private final MockDataService data;
    private final ChaosService chaos;
    private final LeaderboardService leaderboard;
    private final long submitDelayMs;
    private final long graceSeconds;
    private final Map<String, ExamSession> sessions = new ConcurrentHashMap<>();

    public ExamSessionService(MockDataService data, ChaosService chaos, LeaderboardService leaderboard,
                              @Value("${app.simulation.submit-delay-ms:1500}") long submitDelayMs,
                              @Value("${app.exam.grace-seconds:30}") long graceSeconds) {
        this.data = data;
        this.chaos = chaos;
        this.leaderboard = leaderboard;
        this.submitDelayMs = submitDelayMs;
        this.graceSeconds = graceSeconds;
    }

    public void reset() {
        sessions.clear();
    }

    // ---- Countdown timer -------------------------------------------------------------------------

    /** Starts an attempt, or resumes the student's unfinished one (e.g. after a browser crash). */
    public SessionStarted start(String examId, String studentId) {
        ExamSettings settings = data.settings(examId);
        data.student(studentId);
        Instant now = Instant.now();
        ExamSession existing = sessions.values().stream()
                .filter(s -> s.getExamId().equals(examId) && s.getStudentId().equals(studentId))
                .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS && remainingSeconds(s, now) > -graceSeconds)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return started(existing, now, true);
        }
        ExamSession session = new ExamSession(UUID.randomUUID().toString(), examId, studentId, now,
                settings.durationSeconds());
        sessions.put(session.getSessionId(), session);
        leaderboard.studentStarted(studentId);
        return started(session, now, false);
    }

    public SessionStarted session(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            return started(session, Instant.now(), true);
        }
    }

    public TimeRemaining timeRemaining(String sessionId) {
        ExamSession session = find(sessionId);
        ExamSettings settings = data.settings(session.getExamId());
        Instant now = Instant.now();
        long remaining = Math.max(0, remainingSeconds(session, now));
        return new TimeRemaining(sessionId, now, session.getStartedAt(), session.endsAt(),
                session.getDurationSeconds(), remaining, settings.warningAtSeconds(),
                remaining > 0 && remaining <= settings.warningAtSeconds(), remaining == 0);
    }

    // ---- Auto-save -------------------------------------------------------------------------------

    public SaveAnswersResponse save(String sessionId, SaveAnswersRequest request) {
        ExamSession session = find(sessionId);
        if (chaos.consumeSaveFailure()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SIMULATED_FAILURE",
                    "Simulated network failure while saving. Retry.");
        }
        if (request == null || request.answers() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANSWERS_REQUIRED", "Body must contain an 'answers' object");
        }
        synchronized (session) {
            requireWritable(session);
            Map<String, String> validated = validateAnswers(session.getExamId(), request.answers());
            mergeAnswers(session, validated);
            if (request.currentQuestionIndex() != null) {
                session.setCurrentQuestionIndex(clampIndex(session, request.currentQuestionIndex()));
            }
            session.incrementSaveCount();
            session.setLastSavedAt(Instant.now());
            return saveResponse(session, true);
        }
    }

    /** The last saved answers, used to restore the exam after a crash or refresh. */
    public SaveAnswersResponse savedAnswers(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            return saveResponse(session, session.getLastSavedAt() != null);
        }
    }

    // ---- Shared exam state -----------------------------------------------------------------------

    public ExamState state(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            return toState(session);
        }
    }

    public ExamState patchState(String sessionId, ExamStatePatch patch) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            requireWritable(session);
            if (patch.currentQuestionIndex() != null) {
                session.setCurrentQuestionIndex(clampIndex(session, patch.currentQuestionIndex()));
            }
            if (patch.flaggedQuestionIds() != null) {
                for (String questionId : patch.flaggedQuestionIds()) {
                    data.question(session.getExamId(), questionId);
                }
                session.getFlaggedQuestionIds().clear();
                session.getFlaggedQuestionIds().addAll(patch.flaggedQuestionIds());
            }
            return toState(session);
        }
    }

    // ---- Tab-switch detection --------------------------------------------------------------------

    public TabSwitchSummary recordTabSwitch(String sessionId, TabSwitchRequest request) {
        ExamSession session = find(sessionId);
        Instant now = Instant.now();
        Instant hiddenAt = now;
        if (request != null && request.hiddenAt() != null && !request.hiddenAt().isBlank()) {
            try {
                hiddenAt = Instant.parse(request.hiddenAt());
            } catch (DateTimeParseException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMESTAMP",
                        "hiddenAt must be an ISO-8601 instant such as 2026-10-20T10:15:30Z");
            }
        }
        synchronized (session) {
            if (session.getStatus() == SessionStatus.SUBMITTED) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "Exam already submitted");
            }
            List<TabSwitchEvent> events = session.getTabSwitchEvents();
            events.add(new TabSwitchEvent(events.size() + 1, hiddenAt, now));
            return tabSummary(session);
        }
    }

    public TabSwitchSummary tabSwitches(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            return tabSummary(session);
        }
    }

    // ---- "5 minutes left" warning ----------------------------------------------------------------

    public WarningAck warningShown(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            boolean firstTime = session.getWarningShownAt() == null;
            if (firstTime) {
                session.setWarningShownAt(Instant.now());
            }
            return new WarningAck(sessionId, firstTime, session.getWarningShownAt(),
                    Math.max(0, remainingSeconds(session, Instant.now())));
        }
    }

    // ---- Submit (idempotent) ---------------------------------------------------------------------

    /**
     * Grades the attempt once. Every call is counted; calls after the first return the original result
     * with alreadySubmitted=true. The artificial delay keeps the first request in flight long enough for
     * a double click to land, which is what exhaustMap should prevent.
     */
    public SubmitResponse submit(String sessionId, SubmitRequest request) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            session.incrementSubmitRequestCount();
        }
        sleep(submitDelayMs);
        SubmitResult result;
        int count;
        synchronized (session) {
            count = session.getSubmitRequestCount();
            if (session.getStatus() == SessionStatus.SUBMITTED) {
                return new SubmitResponse(sessionId, SessionStatus.SUBMITTED, true, count, session.getResult());
            }
            if (request != null && request.answers() != null) {
                mergeAnswers(session, validateAnswers(session.getExamId(), request.answers()));
            }
            String reason = request != null && "TIMER".equalsIgnoreCase(request.reason()) ? "TIMER" : "MANUAL";
            result = grade(session, reason);
            session.setResult(result);
            session.setStatus(SessionStatus.SUBMITTED);
        }
        leaderboard.studentSubmitted(session.getStudentId(), result);
        return new SubmitResponse(sessionId, SessionStatus.SUBMITTED, false, count, result);
    }

    public SubmitResponse result(String sessionId) {
        ExamSession session = find(sessionId);
        synchronized (session) {
            return new SubmitResponse(sessionId, session.getStatus(), session.getStatus() == SessionStatus.SUBMITTED,
                    session.getSubmitRequestCount(), session.getResult());
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private ExamSession find(String sessionId) {
        ExamSession session = sessions.get(sessionId);
        if (session == null) {
            throw ApiException.notFound("Session", sessionId);
        }
        return session;
    }

    private void requireWritable(ExamSession session) {
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "Exam already submitted");
        }
        if (remainingSeconds(session, Instant.now()) < -graceSeconds) {
            throw new ApiException(HttpStatus.CONFLICT, "TIME_UP", "Time is up. Submit the exam.");
        }
    }

    private Map<String, String> validateAnswers(String examId, Map<String, String> answers) {
        Map<String, String> validated = new LinkedHashMap<>();
        answers.forEach((questionId, optionId) -> {
            Question question = data.question(examId, questionId);
            if (optionId != null && question.options().stream().map(Option::id).noneMatch(optionId::equals)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPTION",
                        "Option '" + optionId + "' does not exist on question " + questionId);
            }
            validated.put(questionId, optionId);
        });
        return validated;
    }

    private static void mergeAnswers(ExamSession session, Map<String, String> answers) {
        answers.forEach((questionId, optionId) -> {
            if (optionId == null) {
                session.getAnswers().remove(questionId);
            } else {
                session.getAnswers().put(questionId, optionId);
            }
        });
    }

    private int clampIndex(ExamSession session, int index) {
        int total = data.questions(session.getExamId()).size();
        return Math.clamp(index, 0, total - 1);
    }

    private SubmitResult grade(ExamSession session, String reason) {
        List<Question> questions = data.questions(session.getExamId());
        ExamSettings settings = data.settings(session.getExamId());
        int score = 0;
        int correct = 0;
        int wrong = 0;
        for (Question q : questions) {
            String answer = session.getAnswers().get(q.id());
            if (answer == null) {
                continue;
            }
            if (answer.equals(data.correctOption(q.id()))) {
                correct++;
                score += q.marks();
            } else {
                wrong++;
            }
        }
        Instant now = Instant.now();
        double percentage = LeaderboardService.percentage(score, settings.totalMarks());
        long taken = Math.min(session.getDurationSeconds(),
                Duration.between(session.getStartedAt(), now).toSeconds());
        return new SubmitResult(score, settings.totalMarks(), percentage, percentage >= settings.passPercentage(),
                correct, wrong, questions.size() - correct - wrong, reason, now, taken);
    }

    private SessionStarted started(ExamSession session, Instant now, boolean resumed) {
        return new SessionStarted(session.getSessionId(), session.getExamId(), session.getStudentId(), now,
                session.getStartedAt(), session.endsAt(), session.getDurationSeconds(),
                Math.max(0, remainingSeconds(session, now)), resumed);
    }

    private SaveAnswersResponse saveResponse(ExamSession session, boolean saved) {
        return new SaveAnswersResponse(session.getSessionId(), saved, session.getLastSavedAt(),
                session.getSaveCount(), session.getAnswers().size(),
                data.questions(session.getExamId()).size(), Map.copyOf(session.getAnswers()));
    }

    private ExamState toState(ExamSession session) {
        return new ExamState(session.getSessionId(), session.getExamId(), session.getStudentId(),
                session.getStatus(), session.getCurrentQuestionIndex(), data.questions(session.getExamId()).size(),
                session.getAnswers().size(), Map.copyOf(session.getAnswers()),
                List.copyOf(session.getFlaggedQuestionIds()), Math.max(0, remainingSeconds(session, Instant.now())),
                session.getTabSwitchEvents().size(), session.getLastSavedAt());
    }

    private TabSwitchSummary tabSummary(ExamSession session) {
        int max = data.settings(session.getExamId()).maxTabSwitches();
        int count = session.getTabSwitchEvents().size();
        return new TabSwitchSummary(session.getSessionId(), count, max, Math.max(0, max - count), count > max,
                List.copyOf(session.getTabSwitchEvents()));
    }

    private static long remainingSeconds(ExamSession session, Instant now) {
        return Duration.between(now, session.endsAt()).toSeconds();
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
