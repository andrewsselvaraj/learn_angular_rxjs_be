package com.learn.rxjs.exam.config;

import com.learn.rxjs.exam.ws.LeaderboardWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Plain WebSocket (no STOMP), so the Angular side can use webSocket() from rxjs/webSocket directly. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LeaderboardWebSocketHandler leaderboardHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(LeaderboardWebSocketHandler leaderboardHandler,
                           @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.leaderboardHandler = leaderboardHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(leaderboardHandler, "/ws/leaderboard").setAllowedOrigins(allowedOrigins);
    }
}
