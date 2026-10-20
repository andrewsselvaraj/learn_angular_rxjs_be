package com.learn.rxjs.exam.web.dto;

import com.learn.rxjs.exam.model.ExamSettings;
import com.learn.rxjs.exam.model.Question;
import com.learn.rxjs.exam.model.Student;
import java.util.List;

/** Server-side equivalent of forkJoin({ questions, student, settings }), for comparison. */
public record StartScreen(List<Question> questions, Student student, ExamSettings settings) {
}
