package com.goldstack.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.error.ApiError;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.GlobalExceptionHandler;
import feign.FeignException;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.InputStream;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(ErrorDecoder.class)
public class FeignSupportAutoConfiguration {

    @Bean
    public ErrorDecoder apiErrorDecoder(ObjectMapper mapper) {
        ErrorDecoder fallback = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            if (response.body() != null && response.status() < 500) {
                try (InputStream body = response.body().asInputStream()) {
                    ApiError err = mapper.readValue(body, ApiError.class);
                    return new ApiException(HttpStatus.valueOf(response.status()), err.error(), err.message());
                } catch (Exception ignored) {

                }
            }
            return fallback.decode(methodKey, response);
        };
    }

    @Bean
    public FeignErrors feignErrors(GlobalExceptionHandler errors) {
        return new FeignErrors(errors);
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class FeignErrors {
        private static final Logger log = LoggerFactory.getLogger(FeignErrors.class);
        private final GlobalExceptionHandler errors;

        FeignErrors(GlobalExceptionHandler errors) {
            this.errors = errors;
        }

        @ExceptionHandler(FeignException.class)
        public ResponseEntity<ApiError> dependencyDown(FeignException ex, HttpServletRequest req) {
            log.error("Call to another service failed on {}: {}", req.getRequestURI(), ex.getMessage());
            return errors.build(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "A GoldStack service we depend on is not responding. Please try again shortly.", req, List.of());
        }
    }
}
