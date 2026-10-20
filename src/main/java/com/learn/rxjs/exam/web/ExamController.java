package com.learn.rxjs.exam.web;

import com.learn.rxjs.exam.model.ExamSettings;
import com.learn.rxjs.exam.model.Leaderboard;
import com.learn.rxjs.exam.model.Question;
import com.learn.rxjs.exam.model.RxjsScenario;
import com.learn.rxjs.exam.service.ExamSessionService;
import com.learn.rxjs.exam.service.LeaderboardService;
import com.learn.rxjs.exam.service.MockDataService;
import com.learn.rxjs.exam.web.dto.SessionStarted;
import com.learn.rxjs.exam.web.dto.StartScreen;
import com.learn.rxjs.exam.web.dto.StartSessionRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final MockDataService data;
    private final ExamSessionService sessions;
    private final LeaderboardService leaderboard;

    public ExamController(MockDataService data, ExamSessionService sessions, LeaderboardService leaderboard) {
        this.data = data;
        this.sessions = sessions;
        this.leaderboard = leaderboard;
    }

    @GetMapping
    public List<ExamSettings> exams() {
        return data.allExams();
    }

    /**
     * Scenario 5: load the exam start screen.
     *
     * forkJoin source: exam settings. Also feeds scenario 1 (durationSeconds), 2 (autoSaveDebounceMs),
     * 4 (maxTabSwitches), 7 (saveRetryCount, saveRetryDelayMs), 8 (warningAtSeconds) and 9 (leaderboardPollIntervalMs).
     * <pre>forkJoin({ questions, student, settings })</pre>
     *
     * @see RxjsScenario#START_SCREEN
     */
    @GetMapping("/{examId}/settings")
    public ExamSettings settings(@PathVariable String examId) {
        return data.settings(examId);
    }

    /**
     * Scenario 5: load the exam start screen.
     *
     * forkJoin source: the questions, without correct answers.
     * <pre>forkJoin({ questions, student, settings })</pre>
     *
     * @see RxjsScenario#START_SCREEN
     */
    @GetMapping("/{examId}/questions")
    public List<Question> questions(@PathVariable String examId) {
        return data.questions(examId);
    }

    @GetMapping("/{examId}/questions/{questionId}")
    public Question question(@PathVariable String examId, @PathVariable String questionId) {
        return data.question(examId, questionId);
    }

    /**
     * Scenario 5: load the exam start screen.
     *
     * All three forkJoin sources in one response, for comparing against the client-side forkJoin.
     *
     * @see RxjsScenario#START_SCREEN
     */
    @GetMapping("/{examId}/start-screen")
    public StartScreen startScreen(@PathVariable String examId,
                                   @RequestParam(defaultValue = MockDataService.DEFAULT_STUDENT_ID) String studentId) {
        return new StartScreen(data.questions(examId), data.student(studentId), data.settings(examId));
    }

    /**
     * Scenario 1: countdown timer.
     *
     * Starts the attempt (or resumes an unfinished one) and returns the server clock.
     * Seed the countdown with remainingSeconds, and call submitExam() when it completes:
     * <pre>timer(0, 1000).pipe(map(s =&gt; duration - s), takeWhile(t =&gt; t &gt;= 0))</pre>
     *
     * @see RxjsScenario#COUNTDOWN_TIMER
     */
    @PostMapping("/{examId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionStarted start(@PathVariable String examId, @RequestBody(required = false) StartSessionRequest request) {
        String studentId = request != null && request.studentId() != null
                ? request.studentId() : MockDataService.DEFAULT_STUDENT_ID;
        return sessions.start(examId, studentId);
    }

    /**
     * Scenario 9: live results or leaderboard (teacher view).
     *
     * See who has submitted as it happens by polling this endpoint:
     * <pre>interval(10000).pipe(switchMap(() =&gt; http.get(url)))</pre>
     * or use the WebSocket at /ws/leaderboard instead.
     *
     * @see RxjsScenario#LIVE_LEADERBOARD
     */
    @GetMapping("/{examId}/leaderboard")
    public Leaderboard leaderboard(@PathVariable String examId) {
        return leaderboard.leaderboard(examId);
    }
}
