package com.learn.rxjs.exam.model;

import java.time.Instant;

public record TabSwitchEvent(int sequence, Instant hiddenAt, Instant receivedAt) {
}
