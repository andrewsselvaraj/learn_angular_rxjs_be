# learn_angular_rxjs_be

A Spring Boot backend for an online exam, built to practise RxJS patterns in Angular. All data is mocked in memory (no database), and everything resets when the server restarts or when you call `POST /api/admin/reset`.

The Angular front end will live in **learn_angular_rxjs_fe**. CORS and the WebSocket are open to `http://localhost:4200`.

## Run it

Requires Java 21. Maven is optional because the wrapper is included.

```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test                     # API tests
```

Settings live in `src/main/resources/application.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `app.cors.allowed-origins` | `http://localhost:4200,http://127.0.0.1:4200` | Origins allowed for REST and WebSocket |
| `app.simulation.enabled` | `true` | Mock classmates start and submit on their own |
| `app.simulation.classmate-submit-interval-ms` | `15000` | How often a mock classmate moves forward |
| `app.simulation.submit-delay-ms` | `1500` | Delay on `/submit`, so a double click lands while the first request is in flight |
| `app.exam.duration-seconds` | `1800` | Exam length; lower it to test the warning and auto-submit |
| `app.exam.grace-seconds` | `30` | Saves are still accepted this long after the timer ends |

On a server, set the `PORT` and `CORS_ALLOWED_ORIGINS` environment variables instead of editing the file.

## Deploy to OpenShift

Log in with `oc login`, then run `./scripts/deploy-backend.sh`. See [deploy/README.md](deploy/README.md).

## Mock data

* One exam, `EXAM-101` "Angular & RxJS Fundamentals": 20 multiple-choice questions, 5 marks each, 30 minutes, warning at 300 seconds, pass mark 40%.
* 15 students, `S1001` to `S1015`. `S1001` (Asha Raman) is "the current student", also reachable as `/api/students/me`.
* The leaderboard starts with some classmates submitted, some in progress and some not started.

## Typical flow

1. Start screen: `forkJoin` the questions, the student and the settings.
2. `POST /api/exams/EXAM-101/sessions` to get a `sessionId` and the server clock.
3. Answer questions. Auto-save with `POST /api/sessions/{sessionId}/answers`.
4. `POST /api/sessions/{sessionId}/submit` from the Submit button or when the timer reaches zero.

## The nine RxJS scenarios

An online exam app is a great fit for RxJS. These are the places the Angular app uses it, and each one has its own endpoints below:

1. **Countdown timer:** show the time left and submit automatically when it reaches zero. `timer(0, 1000).pipe(map(s => duration - s), takeWhile(t => t >= 0))`, and when it completes, call `submitExam()`.
2. **Auto-save answers:** save to the server a moment after the student picks an answer, so nothing is lost if the browser crashes. `answers$.pipe(debounceTime(2000), switchMap(a => http.post('/save', a)))`
3. **Exam state shared across components:** the question panel, the "answered 7/20" bar and the question navigator all read the same state. Use a `BehaviorSubject<ExamState>` in an `ExamService`.
4. **Tab-switch or cheating detection:** count how many times the student leaves the page. `fromEvent(document, 'visibilitychange').pipe(filter(() => document.hidden))`
5. **Load the exam start screen:** fetch the questions, the student and the exam settings together. `forkJoin({ questions, student, settings })`
6. **Prevent double submit:** a nervous double click on Submit should send only one request. `submitClicks$.pipe(exhaustMap(() => http.post('/submit', answers)))`
7. **Network drop handling:** retry saves, and show "offline" when the connection is lost. `retry({ count: 3, delay: 2000 })`, plus `fromEvent(window, 'offline')`
8. **"5 minutes left" warning:** `timeLeft$.pipe(filter(t => t === 300))` shows a popup once.
9. **Live results or leaderboard (teacher view):** see who has submitted as it happens, using `interval(10000)` with `switchMap(http.get)`, or a WebSocket.

If you want to start small, the timer, auto-save and double-submit protection give the biggest benefit for the least code.

The same list lives in the code as the `RxjsScenario` enum (`model/RxjsScenario.java`), each controller method's Javadoc starts with the scenario it serves, and the API serves the catalogue:

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/scenarios` | All nine scenarios: `number`, `key`, `title`, `description`, `rxjs` snippet and `endpoints` |
| GET | `/api/scenarios/{number}` | One scenario, 1 to 9 |

## Endpoints by RxJS scenario

All REST paths are under `http://localhost:8080`. Times are ISO-8601 UTC strings. Errors share one shape:

```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "code": "ALREADY_SUBMITTED", "message": "Exam already submitted", "path": "/api/sessions/.../answers" }
```

### Scenario 1. Countdown timer (`timer(0, 1000)` + `takeWhile`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/api/exams/{examId}/sessions` | `{ "studentId": "S1001" }` (optional) | `SessionStarted` (201) |
| GET | `/api/sessions/{sessionId}` | | `SessionStarted` |
| GET | `/api/sessions/{sessionId}/time` | | `TimeRemaining` |

Starting again while an attempt is unfinished returns the same session with `resumed: true`, which is how a crashed browser picks up where it left off.

```json
{ "sessionId": "dd93…", "serverTime": "...", "startedAt": "...", "endsAt": "...",
  "durationSeconds": 1800, "remainingSeconds": 1800, "resumed": false }
```

Seed the timer with `remainingSeconds`, and call `/time` to resync after a refresh:

```ts
timer(0, 1000).pipe(
  map(s => remainingSeconds - s),
  takeWhile(t => t >= 0),
).subscribe({ next: t => timeLeft$.next(t), complete: () => submitExam('TIMER') });
```

### Scenario 2. Auto-save answers (`debounceTime` + `switchMap`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/api/sessions/{sessionId}/answers` | `{ "answers": { "Q1": "B" }, "currentQuestionIndex": 0 }` | `SaveAnswersResponse` |
| GET | `/api/sessions/{sessionId}/answers` | | Last saved answers, to restore after a crash |

`answers` maps a question id to an option id (`A` to `D`). Answers are merged, and `null` clears one. Sending the full map every time is fine. Unknown questions or options return 400, and saves after submit or after time is up return 409.

```ts
answers$.pipe(
  debounceTime(2000),
  switchMap(answers => http.post(`${api}/sessions/${id}/answers`, { answers })),
)
```

### Scenario 3. Exam state shared across components (`BehaviorSubject<ExamState>`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/api/sessions/{sessionId}/state` | | `ExamState` |
| PATCH | `/api/sessions/{sessionId}/state` | `{ "currentQuestionIndex": 5, "flaggedQuestionIds": ["Q4"] }` | `ExamState` |

```json
{ "sessionId": "…", "status": "IN_PROGRESS", "currentQuestionIndex": 5, "totalQuestions": 20, "answeredCount": 7,
  "answers": { "Q1": "B", … }, "flaggedQuestionIds": ["Q4"], "remainingSeconds": 1712,
  "tabSwitchCount": 0, "lastSavedAt": "..." }
```

Use the GET as the initial value of the `BehaviorSubject` in `ExamService`; the question panel, the "answered 7/20" bar and the navigator all read from it.

### Scenario 4. Tab-switch / cheating detection (`fromEvent(document, 'visibilitychange')`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/api/sessions/{sessionId}/events/tab-switch` | `{ "hiddenAt": "2026-10-20T10:15:30Z" }` (optional) | `TabSwitchSummary` |
| GET | `/api/sessions/{sessionId}/events/tab-switch` | | `TabSwitchSummary` |

```json
{ "count": 2, "maxAllowed": 3, "remainingAllowed": 1, "limitExceeded": false, "events": [ { "sequence": 1, "hiddenAt": "...", "receivedAt": "..." } ] }
```

```ts
fromEvent(document, 'visibilitychange').pipe(
  filter(() => document.hidden),
  concatMap(() => http.post(`${api}/sessions/${id}/events/tab-switch`, { hiddenAt: new Date().toISOString() })),
)
```

### Scenario 5. Load the exam start screen (`forkJoin`)

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/exams/{examId}/questions` | `Question[]` (no correct answers) |
| GET | `/api/students/{studentId}` (or `/api/students/me`) | `Student` |
| GET | `/api/exams/{examId}/settings` | `ExamSettings` |
| GET | `/api/exams/{examId}/start-screen?studentId=S1001` | All three in one response, for comparison |
| GET | `/api/exams`, `/api/students`, `/api/exams/{examId}/questions/{questionId}` | Lists and single question |

Add `?delayMs=` to each call to see that `forkJoin` waits for the slowest one, and `?failWith=500` to see that one failure fails the whole `forkJoin`.

```ts
forkJoin({
  questions: http.get<Question[]>(`${api}/exams/EXAM-101/questions?delayMs=1200`),
  student:   http.get<Student>(`${api}/students/me?delayMs=300`),
  settings:  http.get<ExamSettings>(`${api}/exams/EXAM-101/settings`),
})
```

`ExamSettings` carries every number the client needs:

```json
{ "examId": "EXAM-101", "durationSeconds": 1800, "totalQuestions": 20, "totalMarks": 100, "passPercentage": 40,
  "warningAtSeconds": 300, "autoSaveDebounceMs": 2000, "saveRetryCount": 3, "saveRetryDelayMs": 2000,
  "maxTabSwitches": 3, "leaderboardPollIntervalMs": 10000, "allowReview": true, ... }
```

### Scenario 6. Prevent double submit (`exhaustMap`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/api/sessions/{sessionId}/submit` | `{ "answers": { … }, "reason": "MANUAL" }` (both optional; `reason` is `MANUAL` or `TIMER`) | `SubmitResponse` |
| GET | `/api/sessions/{sessionId}/result` | | `SubmitResponse` |

Submit is idempotent and takes about 1.5 seconds. `submitRequestCount` counts every POST the server received for the session, so you can see whether `exhaustMap` really sent only one. Requests after the first return the original result with `alreadySubmitted: true`.

```json
{ "status": "SUBMITTED", "alreadySubmitted": false, "submitRequestCount": 1,
  "result": { "score": 65, "totalMarks": 100, "percentage": 65.0, "passed": true, "correct": 13, "wrong": 4,
              "unanswered": 3, "reason": "MANUAL", "submittedAt": "...", "timeTakenSeconds": 1432 } }
```

```ts
submitClicks$.pipe(exhaustMap(() => http.post(`${api}/sessions/${id}/submit`, { answers, reason: 'MANUAL' })))
```

### Scenario 7. Network drop handling (`retry` + `fromEvent(window, 'offline')`)

| Method | Path | Body | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health/ping` | | Cheap reachability check after `online` |
| GET | `/api/chaos` | | Current failure switches |
| PUT | `/api/chaos` | `{ "failNextSaves": 2, "failNextRequests": 0, "extraLatencyMs": 0 }` | Make upcoming calls fail or slow down |
| DELETE | `/api/chaos` | | Turn all switches off |

* `failNextSaves: 2` makes the next two saves answer 503, so `retry({ count: 3, delay: 2000 })` succeeds on the third try. Set it to 5 to see retries run out.
* `failNextRequests` does the same for any `/api` call. `extraLatencyMs` slows every `/api` call.
* Any `/api` call also accepts `?delayMs=` and `?failWith=<status>` for one-off simulation.

For a real offline test, stop the server or use DevTools "Offline"; `fromEvent(window, 'offline')` only fires when the browser loses its connection.

### Scenario 8. "5 minutes left" warning (`filter(t => t === 300)`)

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/exams/{examId}/settings` | `warningAtSeconds` (300) |
| GET | `/api/sessions/{sessionId}/time` | `warningDue` is true when 0 < remaining ≤ 300 |
| POST | `/api/sessions/{sessionId}/events/time-warning` | `WarningAck`: `firstTime` is true only the first time, so a reload does not show the popup again |

To reach the warning and the auto-submit quickly, run with a short exam: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.exam.duration-seconds=360`.

### Scenario 9. Live results / leaderboard (teacher view)

**Polling** with `interval(10000)` + `switchMap`:

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/exams/{examId}/leaderboard` | `Leaderboard` |

```json
{ "examId": "EXAM-101", "generatedAt": "...", "totalStudents": 15, "submittedCount": 6, "inProgressCount": 6, "notStartedCount": 3,
  "entries": [ { "rank": 1, "studentId": "S1005", "name": "Esther Okafor", "status": "SUBMITTED", "score": 90, "percentage": 90.0,
                 "submittedAt": "...", "timeTakenSeconds": 1348, "avatarUrl": "..." },
               { "rank": null, "studentId": "S1006", "name": "Farhan Malik", "status": "IN_PROGRESS", ... } ] }
```

Submitted students are ranked by score, then time taken. Everyone else has `rank: null`.

**WebSocket** at `ws://localhost:8080/ws/leaderboard` (plain WebSocket with JSON, no STOMP), which works with `webSocket()` from `rxjs/webSocket`:

```ts
webSocket<LeaderboardMessage>('ws://localhost:8080/ws/leaderboard')
```

Each message:

```json
{ "type": "SNAPSHOT" | "STARTED" | "SUBMITTED", "sentAt": "...", "changed": { …LeaderboardEntry } | null, "leaderboard": { …Leaderboard } }
```

You get a `SNAPSHOT` on connect, then a message whenever anyone starts or submits, including mock classmates (every 15 seconds by default) and real submits through the API. Send the text `refresh` to get a new snapshot.

### Admin

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/admin/reset` | Clear all sessions, re-seed the leaderboard, turn off chaos |

## Project layout

```
src/main/java/com/learn/rxjs/exam
├── config/   CORS, WebSocket registration, latency/failure interceptor
├── model/    RxjsScenario, Question, Student, ExamSettings, ExamSession, Leaderboard…
├── service/  MockDataService (seed data), ExamSessionService, LeaderboardService, ChaosService
├── web/      REST controllers and request/response records (web/dto)
└── ws/       LeaderboardWebSocketHandler
```
