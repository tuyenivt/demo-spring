package com.example.caching.controller;

import com.example.caching.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/activities")
@RequiredArgsConstructor
public class UserActivityController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<Void> addActivity(@PathVariable String userId, @RequestBody String activity) {
        userService.addUserActivity(userId, activity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/oldest")
    public ResponseEntity<String> getOldest(@PathVariable String userId) {
        var activity = userService.getOldestUserActivity(userId);
        return activity != null ? ResponseEntity.ok(activity) : ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<String>> getAll(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserActivities(userId));
    }
}
