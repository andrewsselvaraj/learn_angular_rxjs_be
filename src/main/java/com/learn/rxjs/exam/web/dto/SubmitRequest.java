package com.learn.rxjs.exam.web.dto;

import java.util.Map;

/** reason is MANUAL (Submit button) or TIMER (countdown reached zero). answers are merged before grading. */
public record SubmitRequest(Map<String, String> answers, String reason) {
}
