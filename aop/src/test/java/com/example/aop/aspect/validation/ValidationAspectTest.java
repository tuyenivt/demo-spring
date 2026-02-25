package com.example.aop.aspect.validation;

import com.example.aop.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ValidationAspectTest {

    @Autowired
    private AccountService accountService;

    @Test
    void getAccountById_withNullId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> accountService.getAccountById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void getAccountById_withOutOfRangeId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> accountService.getAccountById(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be >= 1");
    }
}
