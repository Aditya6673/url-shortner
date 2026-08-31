package com.urlshortner.controller;

import com.urlshortner.document.User;
import com.urlshortner.dto.AnalyticsResponse;
import com.urlshortner.dto.DashboardResponse;
import com.urlshortner.repository.UserRepository;
import com.urlshortner.service.AnalyticsService;
import com.urlshortner.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboardStats() {
        // SecurityConfig enforces authentication; service handles premium scoping
        User user = SecurityUtils.requireCurrentUser(userRepository);
        return ResponseEntity.ok(analyticsService.getDashboardStats(user));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        // SecurityConfig enforces authentication; service checks premium + ownership
        User user = SecurityUtils.requireCurrentUser(userRepository);
        return ResponseEntity.ok(analyticsService.getAnalytics(shortCode, user));
    }
}
