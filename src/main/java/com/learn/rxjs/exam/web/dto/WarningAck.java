package com.learn.rxjs.exam.web.dto;

import java.time.Instant;

/** firstTime is false if the "5 minutes left" popup was already recorded for this session. */
public record WarningAck(String sessionId, boolean firstTime, Instant shownAt, long remainingSeconds) {
}
