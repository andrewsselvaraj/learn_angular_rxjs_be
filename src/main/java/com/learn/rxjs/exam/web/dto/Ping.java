package com.learn.rxjs.exam.web.dto;

import java.time.Instant;

public record Ping(String status, Instant serverTime) {
}
