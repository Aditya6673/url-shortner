package com.urlshortner.service;

import com.urlshortner.document.ClickEvent;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.dto.AnalyticsResponse;
import com.urlshortner.dto.DashboardResponse;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ClickEventRepository;
import com.urlshortner.repository.ShortUrlRepository;
import com.urlshortner.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
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

    /**
     * Per-link analytics. Requires premium and ownership.
     */
    public AnalyticsResponse getAnalytics(String shortCode, User user) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        SecurityUtils.requireOwnerAndPremium(shortUrl, user, "detailed analytics");

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

    /**
     * Dashboard stats, scoped to the authenticated user.
     * Free tier: totals only. Premium tier: totals + ClickEvent-derived breakdowns.
     */
    public DashboardResponse getDashboardStats(User user) {
        // One owner-scoped fetch; every free-tier scalar is derived from it.
        List<ShortUrl> ownerUrls = shortUrlRepository
                .findAllByOwnerIdAndActiveTrueOrderByCreatedAtDesc(user.getId());

        long totalClicks = ownerUrls.stream().mapToLong(ShortUrl::getClickCount).sum();

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long urlsCreatedToday = ownerUrls.stream()
                .filter(u -> u.getCreatedAt().isAfter(startOfToday))
                .count();

        boolean isPremium = user.isPremium();

        List<UrlResponse> topUrls = ownerUrls.stream()
                .sorted(Comparator.comparingLong(ShortUrl::getClickCount).reversed())
                .limit(10)
                .map(url -> UrlResponse.of(url, baseUrl, isPremium))
                .collect(Collectors.toList());

        DashboardResponse.DashboardResponseBuilder builder = DashboardResponse.builder()
                .totalUrls(ownerUrls.size())
                .totalClicks(totalClicks)
                .urlsCreatedToday(urlsCreatedToday)
                .topUrls(topUrls);

        // Premium-only: ClickEvent-derived breakdowns across all owned links
        if (isPremium) {
            List<String> shortCodes = ownerUrls.stream()
                    .map(ShortUrl::getShortCode)
                    .collect(Collectors.toList());

            if (!shortCodes.isEmpty()) {
                LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

                List<ClickEvent> recentEvents = clickEventRepository
                        .findByShortCodeInAndClickedAtAfter(shortCodes, thirtyDaysAgo);

                List<AnalyticsResponse.ClicksByDate> clicksByDate = recentEvents.stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getClickedAt().toLocalDate(), Collectors.counting()))
                        .entrySet().stream()
                        .map(e -> new AnalyticsResponse.ClicksByDate(e.getKey(), e.getValue()))
                        .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                        .collect(Collectors.toList());

                Map<String, Long> browserStats = recentEvents.stream()
                        .collect(Collectors.groupingBy(ClickEvent::getBrowser, Collectors.counting()));

                Map<String, Long> osStats = recentEvents.stream()
                        .collect(Collectors.groupingBy(ClickEvent::getOs, Collectors.counting()));

                Map<String, Long> referrerStats = recentEvents.stream()
                        .filter(c -> c.getReferer() != null)
                        .collect(Collectors.groupingBy(ClickEvent::getReferer, Collectors.counting()));

                List<AnalyticsResponse.RecentClick> recentClicksList = clickEventRepository
                        .findTop20ByShortCodeInOrderByClickedAtDesc(shortCodes)
                        .stream()
                        .map(c -> new AnalyticsResponse.RecentClick(
                                c.getClickedAt(), c.getBrowser(), c.getOs(), c.getReferer()))
                        .collect(Collectors.toList());

                builder.clicksByDate(clicksByDate)
                        .browserStats(browserStats)
                        .osStats(osStats)
                        .referrerStats(referrerStats)
                        .recentClicks(recentClicksList);
            }
        }

        return builder.build();
    }
}
