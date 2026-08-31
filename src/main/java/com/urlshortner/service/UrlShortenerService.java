package com.urlshortner.service;

import com.urlshortner.document.ClickEvent;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.exception.DuplicateAliasException;
import com.urlshortner.exception.PremiumRequiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ClickEventRepository;
import com.urlshortner.repository.ShortUrlRepository;
import com.urlshortner.util.Base62Encoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code-length}")
    private int codeLength;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Set<String> RESERVED_CODES = Set.of(
            "api", "css", "js", "login", "logout", "register",
            "me", "dashboard", "admin", "static", "assets", "favicon.ico"
    );

    /**
     * Create a shortened URL. If the caller is authenticated, sets ownerId.
     * If anonymous, generates a one-time statsToken.
     * Custom aliases require premium.
     */
    public UrlResponse createShortUrl(CreateUrlRequest request, User user) {
        String shortCode;
        boolean hasCustomAlias = request.getCustomAlias() != null
                && !request.getCustomAlias().trim().isEmpty();

        if (hasCustomAlias) {
            // Custom aliases require premium
            if (user == null || !user.isPremium()) {
                throw new PremiumRequiredException("custom short links");
            }

            String alias = request.getCustomAlias().trim();

            // Reject reserved codes
            if (RESERVED_CODES.contains(alias.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Short code '" + alias + "' is reserved and cannot be used as a custom alias");
            }

            if (shortUrlRepository.existsByShortCode(alias)) {
                throw new DuplicateAliasException(alias);
            }
            shortCode = alias;
        } else {
            do {
                shortCode = Base62Encoder.generateRandomCode(codeLength);
            } while (shortUrlRepository.existsByShortCode(shortCode));
        }

        ShortUrl.ShortUrlBuilder builder = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(request.getUrl())
                .customAlias(request.getCustomAlias())
                .expiresAt(request.getExpiresAt());

        String statsToken = null;
        if (user != null) {
            builder.ownerId(user.getId());
        } else {
            // Anonymous link — generate a one-time stats token
            byte[] tokenBytes = new byte[16]; // 128-bit
            SECURE_RANDOM.nextBytes(tokenBytes);
            statsToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            builder.statsToken(statsToken);
        }

        ShortUrl saved = shortUrlRepository.save(builder.build());

        UrlResponse response = UrlResponse.of(saved, baseUrl, user != null && user.isPremium());
        // statsToken is returned only once, in this response; null for owned links
        response.setStatsToken(statsToken);
        return response;
    }

    /**
     * Resolve a short code and track the click. Redirects are always public.
     */
    public String resolveAndTrack(String shortCode, HttpServletRequest request) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(LocalDateTime.now())) {
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

        return shortUrl.getOriginalUrl();
    }

    /**
     * Get all URLs owned by the authenticated user.
     */
    public List<UrlResponse> getAllUrls(String ownerId, boolean isPremium) {
        return shortUrlRepository.findAllByOwnerIdAndActiveTrueOrderByCreatedAtDesc(ownerId).stream()
                .map(url -> UrlResponse.of(url, baseUrl, isPremium))
                .collect(Collectors.toList());
    }

    /**
     * Get a single URL by short code, with authorization.
     * Owner or valid stats-token holder may access.
     */
    public UrlResponse getUrlByShortCode(String shortCode, User user, String statsToken) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        authorizeAccess(shortUrl, user, statsToken);

        return UrlResponse.of(shortUrl, baseUrl, user != null && user.isPremium());
    }

    /**
     * Soft-delete a URL by short code, with authorization.
     * Owner or valid stats-token holder may delete.
     */
    public void deleteUrl(String shortCode, User user, String statsToken) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        authorizeAccess(shortUrl, user, statsToken);

        shortUrl.setActive(false);
        shortUrlRepository.save(shortUrl);
    }

    /**
     * Check ownership / stats-token authorization.
     * Returns 404 (not 403) to avoid leaking link existence.
     */
    private void authorizeAccess(ShortUrl shortUrl, User user, String statsToken) {
        // Owner access
        if (user != null && shortUrl.getOwnerId() != null
                && shortUrl.getOwnerId().equals(user.getId())) {
            return;
        }

        // Anonymous stats-token access
        if (shortUrl.getOwnerId() == null && shortUrl.getStatsToken() != null
                && shortUrl.getStatsToken().equals(statsToken)) {
            return;
        }

        // Not authorized — return 404 per spec (don't leak existence)
        throw new UrlNotFoundException(shortUrl.getShortCode());
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
