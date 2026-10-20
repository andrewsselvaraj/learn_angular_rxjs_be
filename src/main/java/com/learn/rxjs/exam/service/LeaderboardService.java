package com.learn.rxjs.exam.service;

import com.learn.rxjs.exam.model.ExamSettings;
import com.learn.rxjs.exam.model.Leaderboard;
import com.learn.rxjs.exam.model.LeaderboardEntry;
import com.learn.rxjs.exam.model.SessionStatus;
import com.learn.rxjs.exam.model.Student;
import com.learn.rxjs.exam.model.SubmitResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Live results for the teacher view. Seeded with a class where some students have already submitted,
 * and a scheduler that makes mock classmates submit over time so polling and the WebSocket see changes.
 */
@Service
public class LeaderboardService {

    private record Row(SessionStatus status, Integer score, Double percentage, Instant submittedAt, Long timeTakenSeconds) {
        static final Row NOT_STARTED = new Row(SessionStatus.NOT_STARTED, null, null, null, null);
        static final Row IN_PROGRESS = new Row(SessionStatus.IN_PROGRESS, null, null, null, null);
    }

    private final MockDataService data;
    private final ApplicationEventPublisher events;
    private final Map<String, Row> rows = new LinkedHashMap<>();
    /** Students driven by a real client. The simulator leaves them alone. */
    private final Set<String> realStudents = new HashSet<>();

    private final boolean simulationEnabled;

    public LeaderboardService(MockDataService data, ApplicationEventPublisher events,
                              @Value("${app.simulation.enabled:true}") boolean simulationEnabled) {
        this.data = data;
        this.events = events;
        this.simulationEnabled = simulationEnabled;
        reset();
    }

    public synchronized void reset() {
        rows.clear();
        realStudents.clear();
        Instant now = Instant.now();
        int totalMarks = settings().totalMarks();
        List<Student> students = data.allStudents();
        for (int i = 0; i < students.size(); i++) {
            String id = students.get(i).id();
            Row row;
            if (id.equals(MockDataService.DEFAULT_STUDENT_ID) || i >= 12) {
                row = Row.NOT_STARTED;
            } else if (i <= 4) {
                int score = 50 + (i * 35) % 50;
                score -= score % 5;
                row = new Row(SessionStatus.SUBMITTED, score, percentage(score, totalMarks),
                        now.minusSeconds(600L - i * 60), 1200L + i * 37);
            } else {
                row = Row.IN_PROGRESS;
            }
            rows.put(id, row);
        }
    }

    public synchronized Leaderboard leaderboard(String examId) {
        data.settings(examId);
        return build();
    }

    public void studentStarted(String studentId) {
        LeaderboardEntry entry;
        Leaderboard board;
        synchronized (this) {
            realStudents.add(studentId);
            rows.put(studentId, Row.IN_PROGRESS);
            board = build();
            entry = entryFor(board, studentId);
        }
        events.publishEvent(new LeaderboardChangedEvent("STARTED", entry, board));
    }

    public void studentSubmitted(String studentId, SubmitResult result) {
        LeaderboardEntry entry;
        Leaderboard board;
        synchronized (this) {
            realStudents.add(studentId);
            rows.put(studentId, new Row(SessionStatus.SUBMITTED, result.score(), result.percentage(),
                    result.submittedAt(), result.timeTakenSeconds()));
            board = build();
            entry = entryFor(board, studentId);
        }
        events.publishEvent(new LeaderboardChangedEvent("SUBMITTED", entry, board));
    }

    /** One simulated classmate moves forward: NOT_STARTED to IN_PROGRESS, or IN_PROGRESS to SUBMITTED. */
    @Scheduled(fixedDelayString = "${app.simulation.classmate-submit-interval-ms:15000}",
            initialDelayString = "${app.simulation.classmate-submit-interval-ms:15000}")
    public void simulateClassmate() {
        if (!simulationEnabled) {
            return;
        }
        Optional<LeaderboardChangedEvent> event = advanceRandomClassmate();
        event.ifPresent(events::publishEvent);
    }

    synchronized Optional<LeaderboardChangedEvent> advanceRandomClassmate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> inProgress = idsWith(SessionStatus.IN_PROGRESS);
        String type;
        String studentId;
        if (!inProgress.isEmpty()) {
            studentId = inProgress.get(random.nextInt(inProgress.size()));
            int totalMarks = settings().totalMarks();
            int score = random.nextInt(totalMarks / 5 + 1) * 5;
            long timeTaken = random.nextLong(600, settings().durationSeconds());
            rows.put(studentId, new Row(SessionStatus.SUBMITTED, score, percentage(score, totalMarks),
                    Instant.now(), timeTaken));
            type = "SUBMITTED";
        } else {
            List<String> notStarted = idsWith(SessionStatus.NOT_STARTED);
            if (notStarted.isEmpty()) {
                return Optional.empty();
            }
            studentId = notStarted.get(random.nextInt(notStarted.size()));
            rows.put(studentId, Row.IN_PROGRESS);
            type = "STARTED";
        }
        Leaderboard board = build();
        return Optional.of(new LeaderboardChangedEvent(type, entryFor(board, studentId), board));
    }

    private List<String> idsWith(SessionStatus status) {
        List<String> ids = new ArrayList<>();
        rows.forEach((id, row) -> {
            if (row.status() == status && !realStudents.contains(id)
                    && !id.equals(MockDataService.DEFAULT_STUDENT_ID)) {
                ids.add(id);
            }
        });
        return ids;
    }

    private Leaderboard build() {
        List<LeaderboardEntry> submitted = new ArrayList<>();
        List<LeaderboardEntry> others = new ArrayList<>();
        rows.forEach((id, row) -> {
            Student s = data.student(id);
            LeaderboardEntry e = new LeaderboardEntry(null, id, s.name(), s.avatarUrl(), row.status(),
                    row.score(), row.percentage(), row.submittedAt(), row.timeTakenSeconds());
            (row.status() == SessionStatus.SUBMITTED ? submitted : others).add(e);
        });
        submitted.sort(Comparator.comparing(LeaderboardEntry::score, Comparator.reverseOrder())
                .thenComparing(LeaderboardEntry::timeTakenSeconds)
                .thenComparing(LeaderboardEntry::submittedAt));
        others.sort(Comparator.comparing((LeaderboardEntry e) -> e.status() == SessionStatus.IN_PROGRESS ? 0 : 1)
                .thenComparing(LeaderboardEntry::name));
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < submitted.size(); i++) {
            LeaderboardEntry e = submitted.get(i);
            entries.add(new LeaderboardEntry(i + 1, e.studentId(), e.name(), e.avatarUrl(), e.status(),
                    e.score(), e.percentage(), e.submittedAt(), e.timeTakenSeconds()));
        }
        entries.addAll(others);
        int inProgress = (int) others.stream().filter(e -> e.status() == SessionStatus.IN_PROGRESS).count();
        return new Leaderboard(MockDataService.EXAM_ID, Instant.now(), entries.size(), submitted.size(),
                inProgress, others.size() - inProgress, List.copyOf(entries));
    }

    private static LeaderboardEntry entryFor(Leaderboard board, String studentId) {
        return board.entries().stream().filter(e -> e.studentId().equals(studentId)).findFirst().orElse(null);
    }

    private ExamSettings settings() {
        return data.settings(MockDataService.EXAM_ID);
    }

    static double percentage(int score, int totalMarks) {
        return Math.round(score * 1000.0 / totalMarks) / 10.0;
    }
}
