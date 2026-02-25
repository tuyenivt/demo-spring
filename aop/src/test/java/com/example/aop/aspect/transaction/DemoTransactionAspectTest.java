package com.example.aop.aspect.transaction;

import com.example.aop.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class DemoTransactionAspectTest {

    @Autowired
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService.resetTransferCounter();
    }

    @Test
    void transferWithRetry_logsBeginAndCommit(CapturedOutput output) {
        var result = accountService.transferFundsWithRetry(1, 2, 100);

        assertThat(result).contains("Transferred 100");
        assertThat(output).contains("BEGIN TX");
        assertThat(output).contains("COMMIT TX");
    }

    @Test
    void transferAndFail_logsRollback(CapturedOutput output) {
        assertThatThrownBy(() -> accountService.transferFundsAndFail(1, 2, 100))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated transfer failure");

        assertThat(output).contains("ROLLBACK TX");
    }
}
