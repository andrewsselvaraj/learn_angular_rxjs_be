package com.learn.rxjs.exam.web;

import com.learn.rxjs.exam.model.RxjsScenario;
import com.learn.rxjs.exam.model.Student;
import com.learn.rxjs.exam.service.MockDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final MockDataService data;

    public StudentController(MockDataService data) {
        this.data = data;
    }

    @GetMapping
    public List<Student> students() {
        return data.allStudents();
    }

    /**
     * Scenario 5: load the exam start screen.
     *
     * forkJoin source: the student. /api/students/me returns the default student S1001.
     * <pre>forkJoin({ questions, student, settings })</pre>
     *
     * @see RxjsScenario#START_SCREEN
     */
    @GetMapping("/{studentId}")
    public Student student(@PathVariable String studentId) {
        return data.student("me".equals(studentId) ? MockDataService.DEFAULT_STUDENT_ID : studentId);
    }
}
