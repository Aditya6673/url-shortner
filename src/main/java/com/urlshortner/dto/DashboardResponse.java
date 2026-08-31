package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // premium fields are absent, not null, on free tier
public class DashboardResponse {
    private long totalUrls;
    private long totalClicks;
    private long urlsCreatedToday;
    private List<UrlResponse> topUrls;

    // Premium-only fields (null for free tier)
    private List<AnalyticsResponse.ClicksByDate> clicksByDate;
    private Map<String, Long> browserStats;
    private Map<String, Long> osStats;
    private Map<String, Long> referrerStats;
    private List<AnalyticsResponse.RecentClick> recentClicks;
}
