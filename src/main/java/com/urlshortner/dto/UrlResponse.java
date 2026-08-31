package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urlshortner.document.ShortUrl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // qrCodeUrl/statsToken are absent, not null, when not granted
public class UrlResponse {
    private String id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private long clickCount;
    private boolean active;
    private String qrCodeUrl;
    private String statsToken;

    /** qrCodeUrl is granted to premium callers only; statsToken is set by the creator, once. */
    public static UrlResponse of(ShortUrl shortUrl, String baseUrl, boolean isPremium) {
        return UrlResponse.builder()
                .id(shortUrl.getId())
                .shortCode(shortUrl.getShortCode())
                .shortUrl(baseUrl + "/" + shortUrl.getShortCode())
                .originalUrl(shortUrl.getOriginalUrl())
                .createdAt(shortUrl.getCreatedAt())
                .expiresAt(shortUrl.getExpiresAt())
                .clickCount(shortUrl.getClickCount())
                .active(shortUrl.isActive())
                .qrCodeUrl(isPremium ? baseUrl + "/api/qr/" + shortUrl.getShortCode() : null)
                .build();
    }
}
