package com.learn.rxjs.exam.web.dto;

import java.util.Map;

/** answers maps questionId to the chosen optionId. A null value clears that answer. */
public record SaveAnswersRequest(Map<String, String> answers, Integer currentQuestionIndex, String clientSavedAt) {
}
