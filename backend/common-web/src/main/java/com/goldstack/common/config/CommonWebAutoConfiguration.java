package com.goldstack.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.goldstack.common.error.ApiError;
import com.goldstack.common.error.GlobalExceptionHandler;
import com.goldstack.common.security.JwtAuthenticationFilter;
import com.goldstack.common.security.JwtService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@AutoConfiguration(before = SecurityAutoConfiguration.class)
@EnableConfigurationProperties(CommonProperties.class)
@Import(GlobalExceptionHandler.class)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock(CommonProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(CommonProperties props, Clock clock) {
        return new JwtService(props.jwt().secret(), props.jwt().expiryMinutes(), clock);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter f) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   CommonProperties props, ObjectMapper mapper, Clock clock)
            throws Exception {
        List<String> publicAny = props.security().publicPaths();
        List<String> publicGet = props.security().publicGetPaths();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error",
                            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/internal/**").permitAll();
                    if (!publicAny.isEmpty()) {
                        auth.requestMatchers(publicAny.toArray(String[]::new)).permitAll();
                    }
                    if (!publicGet.isEmpty()) {
                        auth.requestMatchers(HttpMethod.GET, publicGet.toArray(String[]::new)).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, req, mapper, clock, 401, "UNAUTHORIZED", "Missing or invalid login token"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, req, mapper, clock, 403, "FORBIDDEN", "You are not allowed to do this")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsPlainString() {
        return builder -> builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toPlainString());
            }
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApi(@Value("${spring.application.name:goldstack}") String name) {
        return new OpenAPI()
                .info(new Info().title("GoldStack " + name).version("phase-5"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private static void writeError(HttpServletResponse res, HttpServletRequest req, ObjectMapper mapper, Clock clock,
                                   int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(),
                new ApiError(Instant.now(clock), status, code, message, req.getRequestURI(), List.of()));
    }
}
