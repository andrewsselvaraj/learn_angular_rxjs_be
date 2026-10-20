package com.learn.rxjs.exam.web.dto;

import com.learn.rxjs.exam.model.SessionStatus;
import com.learn.rxjs.exam.model.SubmitResult;

/**
 * submitRequestCount counts every POST to /submit for this session, so the client can check that
 * exhaustMap really sent one request. alreadySubmitted is true for every request after the first.
 */
public record SubmitResponse(
        String sessionId,
        SessionStatus status,
        boolean alreadySubmitted,
        int submitRequestCount,
        SubmitResult result) {
}
