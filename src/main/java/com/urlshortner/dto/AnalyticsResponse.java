package com.urlshortner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private String shortCode;
    private String originalUrl;
    private long totalClicks;
    private List<ClicksByDate> clicksByDate;
    private Map<String, Long> browserStats;
    private Map<String, Long> osStats;
    private Map<String, Long> referrerStats;
    private List<RecentClick> recentClicks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClicksByDate {
        private LocalDate date;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentClick {
        private LocalDateTime clickedAt;
        private String browser;
        private String os;
        private String referer;
    }
}
