package com.learn.rxjs.exam.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.rxjs.exam.model.Leaderboard;
import com.learn.rxjs.exam.model.LeaderboardEntry;
import com.learn.rxjs.exam.service.LeaderboardChangedEvent;
import com.learn.rxjs.exam.service.LeaderboardService;
import com.learn.rxjs.exam.service.MockDataService;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Pushes leaderboard changes. On connect the client gets a SNAPSHOT; afterwards a STARTED or SUBMITTED
 * message each time a student starts or submits. Every message carries the full leaderboard, so the
 * client can simply replace its state. Sending the text "refresh" asks for a new SNAPSHOT.
 */
@Component
public class LeaderboardWebSocketHandler extends TextWebSocketHandler {

    public record Message(String type, Instant sentAt, LeaderboardEntry changed, Leaderboard leaderboard) {
    }

    private static final Logger log = LoggerFactory.getLogger(LeaderboardWebSocketHandler.class);

    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public LeaderboardWebSocketHandler(LeaderboardService leaderboardService, ObjectMapper objectMapper) {
        this.leaderboardService = leaderboardService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);
        sessions.add(safe);
        sendSnapshot(safe);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("refresh".equalsIgnoreCase(message.getPayload().trim())) {
            sessions.stream().filter(s -> s.getId().equals(session.getId())).findFirst().ifPresent(this::sendSnapshot);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
    }

    @EventListener
    public void onLeaderboardChanged(LeaderboardChangedEvent event) {
        broadcast(new Message(event.type(), Instant.now(), event.changed(), event.leaderboard()));
    }

    private void sendSnapshot(WebSocketSession session) {
        Leaderboard board = leaderboardService.leaderboard(MockDataService.EXAM_ID);
        send(session, new Message("SNAPSHOT", Instant.now(), null, board));
    }

    private void broadcast(Message message) {
        sessions.forEach(s -> send(s, message));
    }

    private void send(WebSocketSession session, Message message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (JsonProcessingException e) {
            log.error("Could not serialise leaderboard message", e);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping WebSocket session {}: {}", session.getId(), e.getMessage());
            sessions.remove(session);
        }
    }
}
