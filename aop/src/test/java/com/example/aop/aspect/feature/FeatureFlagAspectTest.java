package com.example.aop.aspect.feature;

import com.example.aop.service.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FeatureFlagAspectTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private FeatureFlagsRegistry featureFlagsRegistry;

    @AfterEach
    void tearDown() {
        featureFlagsRegistry.set("new-pricing-algorithm", true);
    }

    @Test
    void calculatePrice_whenFeatureEnabled_returnsDiscountedPrice() {
        var price = accountService.calculatePrice(1000);

        assertThat(price).isEqualByComparingTo("9.00");
    }

    @Test
    void calculatePrice_whenFeatureDisabled_throws() {
        featureFlagsRegistry.set("new-pricing-algorithm", false);

        assertThatThrownBy(() -> accountService.calculatePrice(1000))
                .isInstanceOf(FeatureDisabledException.class);
    }
}
