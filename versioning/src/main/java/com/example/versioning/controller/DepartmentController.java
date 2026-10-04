package com.example.versioning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DepartmentController {

    /**
     * Demonstrates header versioning: API-Version: 2 (or omitted, as 2 is the default).
     */
    @GetMapping(path = "/location", version = "2")
    public String getLocation() {
        return "Department location v2 is HCM";
    }

    @GetMapping("/v2/location")
    public String getLocationPathVersioned() {
        return "Department location v2 is HCM";
    }
}
