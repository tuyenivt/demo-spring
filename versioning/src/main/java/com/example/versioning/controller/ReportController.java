package com.example.versioning.controller;

import com.example.versioning.dto.ReportV1;
import com.example.versioning.dto.ReportV2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Demonstrates query parameter versioning: GET /api/reports?version=1 (defaults to version 2).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @GetMapping(version = "1")
    public ReportV1 getReportV1() {
        return new ReportV1(1001L, "Weekly Status", "v1 compact report format");
    }

    @GetMapping(version = "2")
    public ReportV2 getReportV2() {
        return new ReportV2(
                1001L,
                "Weekly Status",
                "v2 detailed report format",
                "Platform Team",
                Instant.parse("2026-02-10T00:00:00Z"),
                List.of("ops", "status", "weekly")
        );
    }
}
