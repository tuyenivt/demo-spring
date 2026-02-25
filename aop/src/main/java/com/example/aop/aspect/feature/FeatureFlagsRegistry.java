package com.example.aop.aspect.feature;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeatureFlagsRegistry {

    private final ConcurrentHashMap<String, Boolean> flags = new ConcurrentHashMap<>();

    public FeatureFlagsRegistry() {
        flags.put("new-pricing-algorithm", true);
    }

    public boolean isEnabled(String flag) {
        return flags.getOrDefault(flag, false);
    }

    public void set(String flag, boolean enabled) {
        flags.put(flag, enabled);
    }

    public Map<String, Boolean> snapshot() {
        return Map.copyOf(flags);
    }
}
