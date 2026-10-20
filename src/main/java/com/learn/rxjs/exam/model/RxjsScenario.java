package com.learn.rxjs.exam.model;

import java.util.List;

/**
 * The nine places an online exam app uses RxJS, and the endpoints of this backend that support each one.
 * Served by GET /api/scenarios so the Angular app can show the catalogue too.
 *
 * <p>If you want to start small, the timer (1), auto-save (2) and double-submit protection (6) give the
 * biggest benefit for the least code.
 */
public enum RxjsScenario {

    COUNTDOWN_TIMER(1, "Countdown timer",
            "Show the time left and submit automatically when it reaches zero.",
            "timer(0, 1000).pipe(map(s => duration - s), takeWhile(t => t >= 0)), and when it completes, call submitExam().",
            List.of("POST /api/exams/{examId}/sessions",
                    "GET /api/sessions/{sessionId}/time",
                    "POST /api/sessions/{sessionId}/submit (reason=TIMER)")),

    AUTO_SAVE(2, "Auto-save answers",
            "Save to the server a moment after the student picks an answer, so nothing is lost if the browser crashes.",
            "answers$.pipe(debounceTime(2000), switchMap(a => http.post('/save', a)))",
            List.of("POST /api/sessions/{sessionId}/answers",
                    "GET /api/sessions/{sessionId}/answers")),

    SHARED_EXAM_STATE(3, "Exam state shared across components",
            "The question panel, the \"answered 7/20\" bar and the question navigator all read the same state.",
            "Use a BehaviorSubject<ExamState> in an ExamService.",
            List.of("GET /api/sessions/{sessionId}/state",
                    "PATCH /api/sessions/{sessionId}/state")),

    TAB_SWITCH_DETECTION(4, "Tab-switch or cheating detection",
            "Count how many times the student leaves the page.",
            "fromEvent(document, 'visibilitychange').pipe(filter(() => document.hidden))",
            List.of("POST /api/sessions/{sessionId}/events/tab-switch",
                    "GET /api/sessions/{sessionId}/events/tab-switch")),

    START_SCREEN(5, "Load the exam start screen",
            "Fetch the questions, the student and the exam settings together.",
            "forkJoin({ questions, student, settings })",
            List.of("GET /api/exams/{examId}/questions",
                    "GET /api/students/{studentId}",
                    "GET /api/exams/{examId}/settings",
                    "GET /api/exams/{examId}/start-screen")),

    PREVENT_DOUBLE_SUBMIT(6, "Prevent double submit",
            "A nervous double click on Submit should send only one request.",
            "submitClicks$.pipe(exhaustMap(() => http.post('/submit', answers)))",
            List.of("POST /api/sessions/{sessionId}/submit",
                    "GET /api/sessions/{sessionId}/result")),

    NETWORK_DROP_HANDLING(7, "Network drop handling",
            "Retry saves, and show \"offline\" when the connection is lost.",
            "retry({ count: 3, delay: 2000 }), plus fromEvent(window, 'offline')",
            List.of("GET /api/health/ping",
                    "PUT /api/chaos",
                    "DELETE /api/chaos",
                    "?delayMs= and ?failWith= on any /api call")),

    FIVE_MINUTE_WARNING(8, "\"5 minutes left\" warning",
            "Show a popup once when five minutes are left.",
            "timeLeft$.pipe(filter(t => t === 300)) shows a popup once.",
            List.of("GET /api/exams/{examId}/settings (warningAtSeconds)",
                    "GET /api/sessions/{sessionId}/time (warningDue)",
                    "POST /api/sessions/{sessionId}/events/time-warning")),

    LIVE_LEADERBOARD(9, "Live results or leaderboard (teacher view)",
            "See who has submitted as it happens.",
            "interval(10000) with switchMap(http.get), or a WebSocket.",
            List.of("GET /api/exams/{examId}/leaderboard",
                    "WS /ws/leaderboard"));

    private final int number;
    private final String title;
    private final String description;
    private final String rxjs;
    private final List<String> endpoints;

    RxjsScenario(int number, String title, String description, String rxjs, List<String> endpoints) {
        this.number = number;
        this.title = title;
        this.description = description;
        this.rxjs = rxjs;
        this.endpoints = endpoints;
    }

    public int number() { return number; }
    public String title() { return title; }
    public String description() { return description; }
    public String rxjs() { return rxjs; }
    public List<String> endpoints() { return endpoints; }
}
