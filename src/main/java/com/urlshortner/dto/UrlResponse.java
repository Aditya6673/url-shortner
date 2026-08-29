package com.urlshortner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
