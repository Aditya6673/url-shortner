package com.urlshortner.service;

import com.urlshortner.document.ClickEvent;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.dto.AnalyticsResponse;
import com.urlshortner.dto.DashboardResponse;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ClickEventRepository;
import com.urlshortner.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public AnalyticsResponse getAnalytics(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        List<ClickEvent> clicks = clickEventRepository.findByShortCode(shortCode);

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AnalyticsResponse.ClicksByDate> clicksByDate = clicks.stream()
                .filter(c -> c.getClickedAt().isAfter(thirtyDaysAgo))
                .collect(Collectors.groupingBy(c -> c.getClickedAt().toLocalDate(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new AnalyticsResponse.ClicksByDate(e.getKey(), e.getValue()))
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());

        Map<String, Long> browserStats = clicks.stream()
                .collect(Collectors.groupingBy(ClickEvent::getBrowser, Collectors.counting()));

        Map<String, Long> osStats = clicks.stream()
                .collect(Collectors.groupingBy(ClickEvent::getOs, Collectors.counting()));

        Map<String, Long> referrerStats = clicks.stream()
                .filter(c -> c.getReferer() != null)
                .collect(Collectors.groupingBy(ClickEvent::getReferer, Collectors.counting()));

        List<AnalyticsResponse.RecentClick> recentClicks = clickEventRepository.findTop20ByShortCodeOrderByClickedAtDesc(shortCode)
                .stream()
                .map(c -> new AnalyticsResponse.RecentClick(c.getClickedAt(), c.getBrowser(), c.getOs(), c.getReferer()))
                .collect(Collectors.toList());

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(shortUrl.getOriginalUrl())
                .totalClicks(shortUrl.getClickCount())
                .clicksByDate(clicksByDate)
                .browserStats(browserStats)
                .osStats(osStats)
                .referrerStats(referrerStats)
                .recentClicks(recentClicks)
                .build();
    }

    public DashboardResponse getDashboardStats() {
        long totalUrls = shortUrlRepository.countByActiveTrue();
        long totalClicks = shortUrlRepository.findAllByActiveTrueOrderByCreatedAtDesc().stream().mapToLong(ShortUrl::getClickCount).sum();
        long urlsCreatedToday = shortUrlRepository.countByActiveTrueAndCreatedAtAfter(LocalDate.now().atStartOfDay());

        List<UrlResponse> topUrls = shortUrlRepository.findTop10ByActiveTrueOrderByClickCountDesc()
                .stream()
                .map(this::buildUrlResponse)
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .totalUrls(totalUrls)
                .totalClicks(totalClicks)
                .urlsCreatedToday(urlsCreatedToday)
                .topUrls(topUrls)
                .build();
    }

    private UrlResponse buildUrlResponse(ShortUrl shortUrl) {
        return UrlResponse.builder()
                .id(shortUrl.getId())
                .shortCode(shortUrl.getShortCode())
                .shortUrl(baseUrl + "/" + shortUrl.getShortCode())
                .originalUrl(shortUrl.getOriginalUrl())
                .createdAt(shortUrl.getCreatedAt())
                .expiresAt(shortUrl.getExpiresAt())
                .clickCount(shortUrl.getClickCount())
                .active(shortUrl.isActive())
                .qrCodeUrl(baseUrl + "/api/qr/" + shortUrl.getShortCode())
                .build();
    }
}
