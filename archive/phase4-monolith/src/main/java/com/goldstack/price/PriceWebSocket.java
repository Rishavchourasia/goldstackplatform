package com.goldstack.price;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.price.PriceDtos.LivePrice;
import com.goldstack.price.PriceDtos.PriceUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final PriceService priceService;

    PriceWebSocketHandler(ObjectMapper objectMapper, PriceService priceService) {
        this.objectMapper = objectMapper;
        this.priceService = priceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        sessions.put(session.getId(), safe);
        send(safe, priceService.currentPrice());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @EventListener
    void onPriceUpdated(PriceUpdatedEvent event) {
        sessions.values().forEach(s -> send(s, event.price()));
    }

    private void send(WebSocketSession session, LivePrice price) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(price)));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping websocket session {}: {}", session.getId(), e.getMessage());
            sessions.remove(session.getId());
        }
    }
}

@Configuration
@EnableWebSocket
class PriceWebSocketConfig implements WebSocketConfigurer {

    private final PriceWebSocketHandler handler;

    PriceWebSocketConfig(PriceWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(handler, "/ws/price").setAllowedOriginPatterns("*");
    }
}
