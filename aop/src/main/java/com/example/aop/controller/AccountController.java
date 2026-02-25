package com.example.aop.controller;

import com.example.aop.aspect.feature.FeatureFlagsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final FeatureFlagsRegistry featureFlagsRegistry;

    @GetMapping("/flags")
    public Map<String, Boolean> flags() {
        return featureFlagsRegistry.snapshot();
    }

    @PutMapping("/flags/{flag}")
    public Map<String, Boolean> setFlag(@PathVariable String flag, @RequestParam boolean enabled) {
        featureFlagsRegistry.set(flag, enabled);
        return featureFlagsRegistry.snapshot();
    }
}
