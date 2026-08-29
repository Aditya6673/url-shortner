package com.urlshortner.controller;

import com.urlshortner.dto.AnalyticsResponse;
import com.urlshortner.dto.DashboardResponse;
import com.urlshortner.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboardStats() {
        return ResponseEntity.ok(analyticsService.getDashboardStats());
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        return ResponseEntity.ok(analyticsService.getAnalytics(shortCode));
    }
}
