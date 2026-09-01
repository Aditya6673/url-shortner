package com.urlshortner.service;

import com.urlshortner.document.ClickEvent;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.exception.AccountRequiredException;
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

import java.time.LocalDateTime;
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

    private static final Set<String> RESERVED_CODES = Set.of(
            "api", "css", "js", "login", "logout", "register",
            "me", "dashboard", "admin", "static", "assets", "favicon.ico"
    );

    /**
     * Create a shortened URL. Anonymous callers get the link and nothing else;
     * an account owns it. Expiry dates require an account, custom aliases require premium.
     */
    public UrlResponse createShortUrl(CreateUrlRequest request, User user) {
        String shortCode;
        boolean hasCustomAlias = request.getCustomAlias() != null
                && !request.getCustomAlias().trim().isEmpty();

        if (request.getExpiresAt() != null && user == null) {
            throw new AccountRequiredException("expiry dates");
        }

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

        ShortUrl link = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(request.getUrl())
                .customAlias(request.getCustomAlias())
                .expiresAt(request.getExpiresAt())
                .ownerId(user != null ? user.getId() : null)
                .build();

        return UrlResponse.of(shortUrlRepository.save(link), baseUrl, user);
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
    public List<UrlResponse> getAllUrls(User owner) {
        return shortUrlRepository.findAllByOwnerIdAndActiveTrueOrderByCreatedAtDesc(owner.getId()).stream()
                .map(url -> UrlResponse.of(url, baseUrl, owner))
                .collect(Collectors.toList());
    }

    /**
     * Get a single URL by short code. Owner only.
     */
    public UrlResponse getUrlByShortCode(String shortCode, User user) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        authorizeOwner(shortUrl, user);

        return UrlResponse.of(shortUrl, baseUrl, user);
    }

    /**
     * Soft-delete a URL by short code. Owner only.
     */
    public void deleteUrl(String shortCode, User user) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        authorizeOwner(shortUrl, user);

        shortUrl.setActive(false);
        shortUrlRepository.save(shortUrl);
    }

    /**
     * Anonymous links have no owner and no way back in, so only the owner can read or
     * delete one. Returns 404 (not 403) to avoid leaking link existence.
     */
    private void authorizeOwner(ShortUrl shortUrl, User user) {
        if (user == null || shortUrl.getOwnerId() == null
                || !shortUrl.getOwnerId().equals(user.getId())) {
            throw new UrlNotFoundException(shortUrl.getShortCode());
        }
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
