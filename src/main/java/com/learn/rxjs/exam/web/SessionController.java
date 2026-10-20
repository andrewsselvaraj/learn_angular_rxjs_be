package com.learn.rxjs.exam.web;

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

    /** Countdown timer + "5 minutes left": server-authoritative remaining time. */
    @GetMapping("/time")
    public TimeRemaining time(@PathVariable String sessionId) {
        return sessions.timeRemaining(sessionId);
    }

    /** Auto-save target for debounceTime(2000) + switchMap. */
    @PostMapping("/answers")
    public SaveAnswersResponse save(@PathVariable String sessionId, @RequestBody(required = false) SaveAnswersRequest request) {
        return sessions.save(sessionId, request);
    }

    /** Restore answers after a refresh or crash. */
    @GetMapping("/answers")
    public SaveAnswersResponse savedAnswers(@PathVariable String sessionId) {
        return sessions.savedAnswers(sessionId);
    }

    /** Initial value for BehaviorSubject<ExamState>. */
    @GetMapping("/state")
    public ExamState state(@PathVariable String sessionId) {
        return sessions.state(sessionId);
    }

    /** Navigator updates: current question index and flagged questions. */
    @PatchMapping("/state")
    public ExamState patchState(@PathVariable String sessionId, @RequestBody ExamStatePatch patch) {
        return sessions.patchState(sessionId, patch);
    }

    /** Called each time fromEvent(document, 'visibilitychange') sees document.hidden === true. */
    @PostMapping("/events/tab-switch")
    public TabSwitchSummary tabSwitch(@PathVariable String sessionId, @RequestBody(required = false) TabSwitchRequest request) {
        return sessions.recordTabSwitch(sessionId, request);
    }

    @GetMapping("/events/tab-switch")
    public TabSwitchSummary tabSwitches(@PathVariable String sessionId) {
        return sessions.tabSwitches(sessionId);
    }

    /** Records that the "5 minutes left" popup was shown. firstTime tells the client whether to show it. */
    @PostMapping("/events/time-warning")
    public WarningAck timeWarning(@PathVariable String sessionId) {
        return sessions.warningShown(sessionId);
    }

    /** Idempotent submit for exhaustMap; also the target of the timer's auto-submit (reason=TIMER). */
    @PostMapping("/submit")
    public SubmitResponse submit(@PathVariable String sessionId, @RequestBody(required = false) SubmitRequest request) {
        return sessions.submit(sessionId, request);
    }

    @GetMapping("/result")
    public SubmitResponse result(@PathVariable String sessionId) {
        return sessions.result(sessionId);
    }
}
