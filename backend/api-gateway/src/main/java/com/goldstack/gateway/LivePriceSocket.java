package com.goldstack.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;

@Component
class LivePriceSocket implements WebSocketHandler {

    private final Sinks.Many<String> prices = Sinks.many().replay().latest();

    @KafkaListener(topics = "gold.price.updated", groupId = "gateway-${random.uuid}",
            properties = "auto.offset.reset=latest")
    void onPrice(String payload) {

        prices.tryEmitNext(payload);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Mono<Void> out = session.send(prices.asFlux().map(session::textMessage));
        Mono<Void> in = session.receive().then();
        return Mono.zip(out, in).then();
    }
}

@Configuration
class WebSocketRoutes {

    @Bean
    HandlerMapping livePriceMapping(LivePriceSocket socket) {
        return new SimpleUrlHandlerMapping(Map.of("/ws/price", socket), -1);
    }
}
