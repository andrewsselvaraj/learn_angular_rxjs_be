package com.learn.rxjs.exam.web;

import com.learn.rxjs.exam.model.RxjsScenario;
import com.learn.rxjs.exam.service.ExamSessionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class SessionController {

    private final ExamSessionService sessions;

    public SessionController(ExamSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public SessionStarted session(@PathVariable String sessionId) {
        return sessions.session(sessionId);
    }

    /**
     * Scenario 1: countdown timer, and scenario 8: "5 minutes left" warning.
     *
     * Server-authoritative remaining time, to resync the countdown after a refresh.
     * warningDue is true once remainingSeconds is at or below warningAtSeconds (300).
     * <pre>timer(0, 1000).pipe(map(s =&gt; duration - s), takeWhile(t =&gt; t &gt;= 0))
     * timeLeft$.pipe(filter(t =&gt; t === 300))</pre>
     *
     * @see RxjsScenario#COUNTDOWN_TIMER
     * @see RxjsScenario#FIVE_MINUTE_WARNING
     */
    @GetMapping("/time")
    public TimeRemaining time(@PathVariable String sessionId) {
        return sessions.timeRemaining(sessionId);
    }

    /**
     * Scenario 2: auto-save answers.
     *
     * Save a moment after the student picks an answer, so nothing is lost if the browser crashes.
     * Answers 503 while PUT /api/chaos failNextSaves is set, for scenario 7's retry.
     * <pre>answers$.pipe(debounceTime(2000), switchMap(a =&gt; http.post('/save', a)))</pre>
     *
     * @see RxjsScenario#AUTO_SAVE
     */
    @PostMapping("/answers")
    public SaveAnswersResponse save(@PathVariable String sessionId, @RequestBody(required = false) SaveAnswersRequest request) {
        return sessions.save(sessionId, request);
    }

    /**
     * Scenario 2: auto-save answers.
     *
     * Restore the last saved answers after a refresh or crash.
     *
     * @see RxjsScenario#AUTO_SAVE
     */
    @GetMapping("/answers")
    public SaveAnswersResponse savedAnswers(@PathVariable String sessionId) {
        return sessions.savedAnswers(sessionId);
    }

    /**
     * Scenario 3: exam state shared across components.
     *
     * Initial value for the BehaviorSubject&lt;ExamState&gt; in ExamService, read by the question panel,
     * the "answered 7/20" bar and the question navigator.
     *
     * @see RxjsScenario#SHARED_EXAM_STATE
     */
    @GetMapping("/state")
    public ExamState state(@PathVariable String sessionId) {
        return sessions.state(sessionId);
    }

    /**
     * Scenario 3: exam state shared across components.
     *
     * Navigator updates: current question index and flagged questions.
     *
     * @see RxjsScenario#SHARED_EXAM_STATE
     */
    @PatchMapping("/state")
    public ExamState patchState(@PathVariable String sessionId, @RequestBody ExamStatePatch patch) {
        return sessions.patchState(sessionId, patch);
    }

    /**
     * Scenario 4: tab-switch or cheating detection.
     *
     * Count how many times the student leaves the page. Call it on each hidden event:
     * <pre>fromEvent(document, 'visibilitychange').pipe(filter(() =&gt; document.hidden))</pre>
     *
     * @see RxjsScenario#TAB_SWITCH_DETECTION
     */
    @PostMapping("/events/tab-switch")
    public TabSwitchSummary tabSwitch(@PathVariable String sessionId, @RequestBody(required = false) TabSwitchRequest request) {
        return sessions.recordTabSwitch(sessionId, request);
    }

    @GetMapping("/events/tab-switch")
    public TabSwitchSummary tabSwitches(@PathVariable String sessionId) {
        return sessions.tabSwitches(sessionId);
    }

    /**
     * Scenario 8: "5 minutes left" warning.
     *
     * Records that the popup was shown. firstTime tells the client whether to show it,
     * so it appears only once even after a reload.
     * <pre>timeLeft$.pipe(filter(t =&gt; t === 300))</pre>
     *
     * @see RxjsScenario#FIVE_MINUTE_WARNING
     */
    @PostMapping("/events/time-warning")
    public WarningAck timeWarning(@PathVariable String sessionId) {
        return sessions.warningShown(sessionId);
    }

    /**
     * Scenario 6: prevent double submit.
     *
     * Idempotent and slow on purpose (about 1.5 s), so a nervous double click lands while the first
     * request is in flight. submitRequestCount shows how many requests reached the server.
     * Also the target of scenario 1's auto-submit (reason=TIMER).
     * <pre>submitClicks$.pipe(exhaustMap(() =&gt; http.post('/submit', answers)))</pre>
     *
     * @see RxjsScenario#PREVENT_DOUBLE_SUBMIT
     * @see RxjsScenario#COUNTDOWN_TIMER
     */
    @PostMapping("/submit")
    public SubmitResponse submit(@PathVariable String sessionId, @RequestBody(required = false) SubmitRequest request) {
        return sessions.submit(sessionId, request);
    }

    @GetMapping("/result")
    public SubmitResponse result(@PathVariable String sessionId) {
        return sessions.result(sessionId);
    }
}
