package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // tier-gated fields are absent, not null, when not granted
public class UrlResponse {
    private String id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Long clickCount;    // boxed so NON_NULL can drop it for anonymous callers
    private boolean active;
    private String qrCodeUrl;

    /**
     * The single place link fields are gated by tier. Anonymous callers get the link itself;
     * an account adds clickCount and expiresAt; premium adds qrCodeUrl.
     *
     * @param viewer the caller, or null when anonymous
     */
    public static UrlResponse of(ShortUrl shortUrl, String baseUrl, User viewer) {
        boolean hasAccount = viewer != null;
        boolean isPremium = viewer != null && viewer.isPremium();

        return UrlResponse.builder()
                .id(shortUrl.getId())
                .shortCode(shortUrl.getShortCode())
                .shortUrl(baseUrl + "/" + shortUrl.getShortCode())
                .originalUrl(shortUrl.getOriginalUrl())
                .createdAt(shortUrl.getCreatedAt())
                .expiresAt(hasAccount ? shortUrl.getExpiresAt() : null)
                .clickCount(hasAccount ? shortUrl.getClickCount() : null)
                .active(shortUrl.isActive())
                .qrCodeUrl(isPremium ? baseUrl + "/api/qr/" + shortUrl.getShortCode() : null)
                .build();
    }
}
