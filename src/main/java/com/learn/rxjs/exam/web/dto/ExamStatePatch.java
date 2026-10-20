package com.learn.rxjs.exam.web.dto;

import java.util.List;

/** Partial update from the question navigator. Null fields are left unchanged. */
public record ExamStatePatch(Integer currentQuestionIndex, List<String> flaggedQuestionIds) {
}
