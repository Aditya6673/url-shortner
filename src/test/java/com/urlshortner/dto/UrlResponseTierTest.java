package com.urlshortner.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.urlshortner.document.Plan;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UrlResponse.of is the only place link fields are gated by tier, so this is where
 * the tier matrix is pinned: anonymous gets the link, an account adds clickCount and
 * expiresAt, premium adds qrCodeUrl.
 */
class UrlResponseTierTest {

    private static final String BASE_URL = "http://localhost:8080";

    private final ShortUrl link = ShortUrl.builder()
            .id("1")
            .shortCode("abc1234")
            .originalUrl("https://example.com")
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .clickCount(42)
            .active(true)
            .build();

    private static User user(Plan plan) {
        return User.builder().id("u1").email("a@b.com").plan(plan).build();
    }

    @Test
    @DisplayName("Anonymous: no clickCount, no expiresAt, no qrCodeUrl")
    void anonymousGetsLinkOnly() {
        UrlResponse r = UrlResponse.of(link, BASE_URL, null);

        assertEquals("abc1234", r.getShortCode());
        assertEquals(BASE_URL + "/abc1234", r.getShortUrl());
        assertNull(r.getClickCount());
        assertNull(r.getExpiresAt());
        assertNull(r.getQrCodeUrl());
    }

    @Test
    @DisplayName("Anonymous: gated fields are absent from the JSON, not null")
    void anonymousJsonOmitsGatedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = mapper.writeValueAsString(UrlResponse.of(link, BASE_URL, null));

        assertFalse(json.contains("clickCount"), json);
        assertFalse(json.contains("expiresAt"), json);
        assertFalse(json.contains("qrCodeUrl"), json);
        assertTrue(json.contains("abc1234"), json);
    }

    @Test
    @DisplayName("Free account: clickCount and expiresAt, still no qrCodeUrl")
    void freeAccountGetsCountsAndExpiry() {
        UrlResponse r = UrlResponse.of(link, BASE_URL, user(Plan.FREE));

        assertEquals(42L, r.getClickCount());
        assertNotNull(r.getExpiresAt());
        assertNull(r.getQrCodeUrl());
    }

    @Test
    @DisplayName("Premium: everything, including qrCodeUrl")
    void premiumGetsEverything() {
        UrlResponse r = UrlResponse.of(link, BASE_URL, user(Plan.PREMIUM));

        assertEquals(42L, r.getClickCount());
        assertNotNull(r.getExpiresAt());
        assertEquals(BASE_URL + "/api/qr/abc1234", r.getQrCodeUrl());
    }
}
