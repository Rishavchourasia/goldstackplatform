package com.goldstack.common.security;

import com.goldstack.TestContainers;
import com.goldstack.user.UserDtos.SignupRequest;
import com.goldstack.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "goldstack.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
@Import(TestContainers.class)
@ActiveProfiles("test")
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired StringRedisTemplate redis;

    @Test
    void sixthRequestInAMinuteIsRejectedWith429() throws Exception {
        var auth = userService.signup(new SignupRequest("rl-" + UUID.randomUUID() + "@test.com", "password123", "RL"));
        String bearer = "Bearer " + auth.token();

        for (int i = 1; i <= 5; i++) {
            mvc.perform(get("/api/wallet").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(5 - i)));
        }
        mvc.perform(get("/api/wallet").header("Authorization", bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));

        String key = "ratelimit:" + auth.user().id();
        assertThat(redis.opsForValue().get(key)).isEqualTo("6");
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }
}
