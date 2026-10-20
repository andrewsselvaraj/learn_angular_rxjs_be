package com.learn.rxjs.exam.web.dto;

/**
 * failNextSaves: the next N save requests answer 503 (practise retry({ count: 3, delay: 2000 })).
 * failNextRequests: the next N requests to any /api endpoint answer 503.
 * extraLatencyMs: added to every /api request.
 */
public record ChaosConfig(Integer failNextSaves, Integer failNextRequests, Integer extraLatencyMs) {
}
