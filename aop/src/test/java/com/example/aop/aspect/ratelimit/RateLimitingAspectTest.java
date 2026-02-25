package com.example.aop.aspect.ratelimit;

import com.example.aop.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RateLimitingAspectTest {

    @Autowired
    private AccountService accountService;

    @Test
    void rateLimitedPing_thirdCallWithinSecond_isRejected() {
        assertThat(accountService.rateLimitedPing()).isEqualTo("pong");
        assertThat(accountService.rateLimitedPing()).isEqualTo("pong");
        assertThatThrownBy(() -> accountService.rateLimitedPing())
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
