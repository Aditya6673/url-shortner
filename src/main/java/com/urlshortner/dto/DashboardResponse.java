package com.urlshortner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private long totalUrls;
    private long totalClicks;
    private long urlsCreatedToday;
    private List<UrlResponse> topUrls;
}
