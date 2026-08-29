package com.urlshortner.service;

import com.urlshortner.document.ClickEvent;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.exception.DuplicateAliasException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ClickEventRepository;
import com.urlshortner.repository.ShortUrlRepository;
import com.urlshortner.util.Base62Encoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code-length}")
    private int codeLength;

    @Value("${app.redis.cache-ttl-hours}")
    private long cacheTtlHours;

    public UrlResponse createShortUrl(CreateUrlRequest request) {
        String shortCode;
        if (request.getCustomAlias() != null && !request.getCustomAlias().trim().isEmpty()) {
            if (shortUrlRepository.existsByShortCode(request.getCustomAlias())) {
                throw new DuplicateAliasException(request.getCustomAlias());
            }
            shortCode = request.getCustomAlias();
        } else {
            do {
                shortCode = Base62Encoder.generateRandomCode(codeLength);
            } while (shortUrlRepository.existsByShortCode(shortCode));
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(request.getUrl())
                .customAlias(request.getCustomAlias())
                .expiresAt(request.getExpiresAt())
                .build();

        ShortUrl saved = shortUrlRepository.save(shortUrl);
        cacheUrl(shortCode, saved.getOriginalUrl());

        return buildUrlResponse(saved);
    }

    public String resolveAndTrack(String shortCode, HttpServletRequest request) {
        String originalUrl = redisTemplate.opsForValue().get(shortCode);

        ShortUrl shortUrl;
        if (originalUrl == null) {
            shortUrl = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new UrlNotFoundException(shortCode));
            originalUrl = shortUrl.getOriginalUrl();
            cacheUrl(shortCode, originalUrl);
        } else {
            shortUrl = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new UrlNotFoundException(shortCode));
        }

        if (!shortUrl.isActive() || (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new UrlNotFoundException(shortCode);
        }

        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
        shortUrlRepository.save(shortUrl);

        String userAgent = request.getHeader("User-Agent");
        ClickEvent clickEvent = ClickEvent.builder()
                .shortCode(shortCode)
                .clickedAt(LocalDateTime.now())
                .ipAddress(request.getRemoteAddr())
                .userAgent(userAgent)
                .referer(request.getHeader("Referer"))
                .browser(parseBrowser(userAgent))
                .os(parseOs(userAgent))
                .build();

        clickEventRepository.save(clickEvent);

        return originalUrl;
    }

    public List<UrlResponse> getAllUrls() {
        return shortUrlRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::buildUrlResponse)
                .collect(Collectors.toList());
    }

    public UrlResponse getUrlByShortCode(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return buildUrlResponse(shortUrl);
    }

    public void deleteUrl(String id) {
        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException(id));
        shortUrl.setActive(false);
        shortUrlRepository.save(shortUrl);
        redisTemplate.delete(shortUrl.getShortCode());
    }

    private void cacheUrl(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(shortCode, originalUrl, cacheTtlHours, TimeUnit.HOURS);
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

    private String parseBrowser(String ua) {
        if (ua == null) return "Unknown";
        String lowerUA = ua.toLowerCase();
        if (lowerUA.contains("edg")) return "Edge";
        if (lowerUA.contains("opr") || lowerUA.contains("opera")) return "Opera";
        if (lowerUA.contains("chrome")) return "Chrome";
        if (lowerUA.contains("firefox")) return "Firefox";
        if (lowerUA.contains("safari")) return "Safari";
        return "Other";
    }

    private String parseOs(String ua) {
        if (ua == null) return "Unknown";
        String lowerUA = ua.toLowerCase();
        if (lowerUA.contains("windows")) return "Windows";
        if (lowerUA.contains("mac")) return "Mac";
        if (lowerUA.contains("linux")) return "Linux";
        if (lowerUA.contains("android")) return "Android";
        if (lowerUA.contains("iphone") || lowerUA.contains("ipad")) return "iOS";
        return "Other";
    }
}
