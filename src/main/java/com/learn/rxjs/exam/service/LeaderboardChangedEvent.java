package com.learn.rxjs.exam.service;

import com.learn.rxjs.exam.model.Leaderboard;
import com.learn.rxjs.exam.model.LeaderboardEntry;

/** Published whenever a student starts or submits, so the WebSocket can push the change. */
public record LeaderboardChangedEvent(String type, LeaderboardEntry changed, Leaderboard leaderboard) {
}
