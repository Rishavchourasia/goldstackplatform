package com.goldstack.gateway;

import com.goldstack.common.security.JwtService;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    Clock clock(@Value("${goldstack.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    JwtService jwtService(@Value("${goldstack.jwt.secret}") String secret, Clock clock) {
        return new JwtService(secret, 1440, clock);
    }

    @Bean
    HttpClientCustomizer shortLivedDns() {
        return client -> client.resolver(DefaultAddressResolverGroup.INSTANCE);
    }
}
