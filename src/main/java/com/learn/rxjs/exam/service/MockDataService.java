package com.learn.rxjs.exam.service;

import com.learn.rxjs.exam.model.ExamSettings;
import com.learn.rxjs.exam.model.Option;
import com.learn.rxjs.exam.model.Question;
import com.learn.rxjs.exam.model.Student;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Hard-coded exam content. Nothing here changes at runtime. */
@Service
public class MockDataService {

    public static final String EXAM_ID = "EXAM-101";
    public static final String DEFAULT_STUDENT_ID = "S1001";

    private final Map<String, ExamSettings> settingsByExam = new LinkedHashMap<>();
    private final Map<String, List<Question>> questionsByExam = new LinkedHashMap<>();
    private final Map<String, String> correctOptionByQuestion = new LinkedHashMap<>();
    private final Map<String, Student> students = new LinkedHashMap<>();

    public MockDataService(@Value("${app.exam.duration-seconds:1800}") int durationSeconds) {
        seedQuestions();
        seedStudents();
        List<Question> questions = questionsByExam.get(EXAM_ID);
        int totalMarks = questions.stream().mapToInt(Question::marks).sum();
        settingsByExam.put(EXAM_ID, new ExamSettings(
                EXAM_ID,
                "Angular & RxJS Fundamentals",
                "Web Development",
                durationSeconds,
                questions.size(),
                totalMarks,
                40,
                5 * 60,
                2000,
                3,
                2000,
                3,
                10000,
                true));
    }

    public List<ExamSettings> allExams() {
        return List.copyOf(settingsByExam.values());
    }

    public ExamSettings settings(String examId) {
        ExamSettings settings = settingsByExam.get(examId);
        if (settings == null) {
            throw ApiException.notFound("Exam", examId);
        }
        return settings;
    }

    public List<Question> questions(String examId) {
        settings(examId);
        return questionsByExam.get(examId);
    }

    public Question question(String examId, String questionId) {
        return questions(examId).stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Question", questionId));
    }

    public String correctOption(String questionId) {
        return correctOptionByQuestion.get(questionId);
    }

    public List<Student> allStudents() {
        return List.copyOf(students.values());
    }

    public Student student(String studentId) {
        Student student = students.get(studentId);
        if (student == null) {
            throw ApiException.notFound("Student", studentId);
        }
        return student;
    }

    private void seedQuestions() {
        List<Question> list = new ArrayList<>();
        add(list, "RxJS", "Which operator cancels the previous inner observable when a new value arrives?",
                "B", "mergeMap", "switchMap", "concatMap", "exhaustMap");
        add(list, "RxJS", "Which operator ignores new values while the current inner observable is still running?",
                "D", "switchMap", "concatMap", "mergeMap", "exhaustMap");
        add(list, "RxJS", "What does forkJoin emit?",
                "C", "Every value of every source", "The first value of the fastest source",
                "The last value of each source, once all complete", "Nothing, it only completes");
        add(list, "RxJS", "Which subject always gives new subscribers the current value?",
                "A", "BehaviorSubject", "Subject", "AsyncSubject", "ReplaySubject(0)");
        add(list, "RxJS", "debounceTime(2000) emits a value when...",
                "B", "every 2 seconds", "2 seconds pass with no new value",
                "the first value arrives, then waits 2 seconds", "the source completes");
        add(list, "RxJS", "timer(0, 1000) emits...",
                "C", "once after 1 second", "every second starting after 1 second",
                "0 immediately, then 1, 2, 3... every second", "a Date every second");
        add(list, "RxJS", "takeWhile(t => t >= 0) completes when...",
                "A", "the predicate first returns false", "the source errors",
                "the predicate returns true", "after one value");
        add(list, "RxJS", "retry({ count: 3, delay: 2000 }) does what on error?",
                "D", "Fails immediately", "Retries forever every 2 seconds",
                "Retries 3 times with no delay", "Resubscribes up to 3 times, waiting 2 seconds each time");
        add(list, "RxJS", "fromEvent(document, 'visibilitychange') creates...",
                "B", "a Promise", "a hot observable of DOM events", "a cold HTTP request", "a Subject");
        add(list, "RxJS", "Which operator only lets through values that match a condition?",
                "C", "map", "tap", "filter", "scan");
        add(list, "Angular", "Which pipe subscribes to an observable in a template and unsubscribes on destroy?",
                "A", "async", "json", "date", "keyvalue");
        add(list, "Angular", "HttpClient observables are...",
                "B", "hot and multicast", "cold, one request per subscription",
                "Promises", "always cached");
        add(list, "Angular", "Where is the best place to keep exam state shared by several components?",
                "D", "A global variable", "localStorage only", "Each component's own field",
                "An injectable service exposing an observable");
        add(list, "Angular", "Which lifecycle hook is the usual place to clean up subscriptions?",
                "C", "ngOnInit", "ngAfterViewInit", "ngOnDestroy", "ngDoCheck");
        add(list, "Angular", "takeUntilDestroyed() comes from which package?",
                "A", "@angular/core/rxjs-interop", "rxjs/operators", "@angular/common/http", "@angular/router");
        add(list, "RxJS", "interval(10000).pipe(switchMap(() => http.get(url))) is a common way to...",
                "B", "debounce input", "poll a server every 10 seconds", "retry a failed request", "open a WebSocket");
        add(list, "RxJS", "Which function from rxjs/webSocket returns a Subject backed by a WebSocket?",
                "C", "fromSocket", "ajax", "webSocket", "connect");
        add(list, "RxJS", "What is the main difference between combineLatest and forkJoin?",
                "D", "There is none", "forkJoin emits on every change",
                "combineLatest waits for completion",
                "combineLatest emits on every change once all have emitted; forkJoin emits once at completion");
        add(list, "RxJS", "share() is used to...",
                "A", "multicast one source subscription to many subscribers", "copy an array",
                "delay emissions", "convert to a Promise");
        add(list, "RxJS", "Which operator handles an error and returns a fallback observable?",
                "B", "finalize", "catchError", "throwError", "retryWhen");
        questionsByExam.put(EXAM_ID, Collections.unmodifiableList(list));
    }

    private void add(List<Question> list, String topic, String text, String correct, String a, String b, String c, String d) {
        int number = list.size() + 1;
        String id = "Q" + number;
        List<Option> options = List.of(
                new Option("A", a), new Option("B", b), new Option("C", c), new Option("D", d));
        list.add(new Question(id, number, topic, text, options, 5));
        correctOptionByQuestion.put(id, correct);
    }

    private void seedStudents() {
        String[][] rows = {
                {"S1001", "Asha Raman"},
                {"S1002", "Ben Carter"},
                {"S1003", "Chloe Nguyen"},
                {"S1004", "Diego Alvarez"},
                {"S1005", "Esther Okafor"},
                {"S1006", "Farhan Malik"},
                {"S1007", "Grace Lee"},
                {"S1008", "Hiro Tanaka"},
                {"S1009", "Isla Murphy"},
                {"S1010", "Jonas Berg"},
                {"S1011", "Kavya Iyer"},
                {"S1012", "Liam O'Brien"},
                {"S1013", "Mei Chen"},
                {"S1014", "Noah Schmidt"},
                {"S1015", "Olivia Rossi"},
        };
        for (String[] row : rows) {
            String id = row[0];
            String name = row[1];
            String email = name.toLowerCase().replaceAll("[^a-z ]", "").replace(' ', '.') + "@example.edu";
            students.put(id, new Student(id, name, email, "R-" + id.substring(1), "CS-2A",
                    "https://api.dicebear.com/9.x/initials/svg?seed=" + name.replace(" ", "%20")));
        }
    }
}
