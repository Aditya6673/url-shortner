package com.urlshortner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortner.document.Plan;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.dto.*;
import com.urlshortner.exception.PremiumRequiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ShortUrlRepository;
import com.urlshortner.repository.UserRepository;
import com.urlshortner.service.AnalyticsService;
import com.urlshortner.service.QrCodeService;
import com.urlshortner.service.UrlShortenerService;
import com.urlshortner.service.UserService;
import com.urlshortner.config.SecurityConfig;
import com.urlshortner.filter.AnonymousRateLimitFilter;
import com.urlshortner.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authorization matrix test for all endpoints.
 * Tests every endpoint against four caller types:
 * 1. Anonymous — no session
 * 2. Free (owner) — authenticated, Plan.FREE, owns the link
 * 3. Premium (non-owner) — authenticated, Plan.PREMIUM, does NOT own the link
 * 4. Premium (owner) — authenticated, Plan.PREMIUM, owns the link
 *
 * Additionally asserts:
 * - No free-tier response body contains ClickEvent-derived fields
 * - qrCodeUrl is absent for non-premium callers
 * - statsToken is present for anonymous POST /api/urls responses
 */
@WebMvcTest({UrlController.class, AnalyticsController.class, QrCodeController.class,
        AuthController.class, UserController.class})
@Import({SecurityConfig.class, AnonymousRateLimitFilter.class})
@TestPropertySource(properties = {
        "app.rate-limit.anonymous-links-per-hour=20",
        "app.billing.mock-upgrade-enabled=false",
        "app.base-url=http://localhost:8080"
})
class AuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private QrCodeService qrCodeService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ShortUrlRepository shortUrlRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    private static final String OWNER_EMAIL = "owner@test.com";
    private static final String OWNER_ID = "owner-123";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final String OTHER_ID = "other-456";
    private static final String SHORT_CODE = "abc1234";
    private static final String STATS_TOKEN = "test-stats-token-abc";

    private User freeOwner;
    private User premiumOwner;
    private User premiumNonOwner;

    @BeforeEach
    void setUp() {
        freeOwner = User.builder()
                .id(OWNER_ID)
                .email(OWNER_EMAIL)
                .plan(Plan.FREE)
                .build();

        premiumOwner = User.builder()
                .id(OWNER_ID)
                .email(OWNER_EMAIL)
                .plan(Plan.PREMIUM)
                .build();

        premiumNonOwner = User.builder()
                .id(OTHER_ID)
                .email(OTHER_EMAIL)
                .plan(Plan.PREMIUM)
                .build();
    }

    // ── POST /api/urls ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/urls")
    class CreateUrl {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous without custom alias → 201 with statsToken")
        void anonymousNoAlias_returns201WithStatsToken() throws Exception {
            CreateUrlRequest req = new CreateUrlRequest();
            req.setUrl("https://example.com");

            UrlResponse resp = UrlResponse.builder()
                    .shortCode(SHORT_CODE)
                    .shortUrl("http://localhost:8080/" + SHORT_CODE)
                    .originalUrl("https://example.com")
                    .statsToken(STATS_TOKEN)
                    .active(true)
                    .build();

            when(urlShortenerService.createShortUrl(any(CreateUrlRequest.class), isNull()))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statsToken").value(STATS_TOKEN))
                    .andExpect(jsonPath("$.qrCodeUrl").doesNotExist());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous with custom alias → 403")
        void anonymousWithAlias_returns403() throws Exception {
            CreateUrlRequest req = new CreateUrlRequest();
            req.setUrl("https://example.com");
            req.setCustomAlias("myalias");

            when(urlShortenerService.createShortUrl(any(CreateUrlRequest.class), isNull()))
                    .thenThrow(new PremiumRequiredException("custom short links"));

            mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free user with custom alias → 403")
        void freeWithAlias_returns403() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));

            CreateUrlRequest req = new CreateUrlRequest();
            req.setUrl("https://example.com");
            req.setCustomAlias("myalias");

            when(urlShortenerService.createShortUrl(any(CreateUrlRequest.class), eq(freeOwner)))
                    .thenThrow(new PremiumRequiredException("custom short links"));

            mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium user with custom alias → 201, no statsToken")
        void premiumWithAlias_returns201() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            CreateUrlRequest req = new CreateUrlRequest();
            req.setUrl("https://example.com");
            req.setCustomAlias("myalias");

            UrlResponse resp = UrlResponse.builder()
                    .shortCode("myalias")
                    .shortUrl("http://localhost:8080/myalias")
                    .originalUrl("https://example.com")
                    .active(true)
                    .qrCodeUrl("http://localhost:8080/api/qr/myalias")
                    .build();

            when(urlShortenerService.createShortUrl(any(CreateUrlRequest.class), eq(premiumOwner)))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statsToken").doesNotExist())
                    .andExpect(jsonPath("$.qrCodeUrl").exists());
        }
    }

    // ── GET /api/urls ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/urls")
    class GetAllUrls {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/urls"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free owner → 200, qrCodeUrl null")
        void freeOwner_returns200NoQrUrl() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));

            UrlResponse resp = UrlResponse.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .active(true)
                    .build();

            when(urlShortenerService.getAllUrls(OWNER_ID, false))
                    .thenReturn(List.of(resp));

            mockMvc.perform(get("/api/urls"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].qrCodeUrl").doesNotExist());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium owner → 200, qrCodeUrl present")
        void premiumOwner_returns200WithQrUrl() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            UrlResponse resp = UrlResponse.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .active(true)
                    .qrCodeUrl("http://localhost:8080/api/qr/" + SHORT_CODE)
                    .build();

            when(urlShortenerService.getAllUrls(OWNER_ID, true))
                    .thenReturn(List.of(resp));

            mockMvc.perform(get("/api/urls"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].qrCodeUrl").exists());
        }
    }

    // ── GET /api/urls/{shortCode} ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/urls/{shortCode}")
    class GetUrlByShortCode {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous without stats token → 404")
        void anonymousNoToken_returns404() throws Exception {
            when(urlShortenerService.getUrlByShortCode(eq(SHORT_CODE), isNull(), isNull()))
                    .thenThrow(new UrlNotFoundException(SHORT_CODE));

            mockMvc.perform(get("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous with valid stats token → 200")
        void anonymousWithToken_returns200() throws Exception {
            UrlResponse resp = UrlResponse.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .active(true)
                    .build();

            when(urlShortenerService.getUrlByShortCode(eq(SHORT_CODE), isNull(), eq(STATS_TOKEN)))
                    .thenReturn(resp);

            mockMvc.perform(get("/api/urls/{shortCode}", SHORT_CODE)
                            .header("X-Stats-Token", STATS_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = OTHER_EMAIL)
        @DisplayName("Premium non-owner → 404")
        void premiumNonOwner_returns404() throws Exception {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(premiumNonOwner));

            when(urlShortenerService.getUrlByShortCode(eq(SHORT_CODE), eq(premiumNonOwner), isNull()))
                    .thenThrow(new UrlNotFoundException(SHORT_CODE));

            mockMvc.perform(get("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium owner → 200")
        void premiumOwner_returns200() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            UrlResponse resp = UrlResponse.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .active(true)
                    .qrCodeUrl("http://localhost:8080/api/qr/" + SHORT_CODE)
                    .build();

            when(urlShortenerService.getUrlByShortCode(eq(SHORT_CODE), eq(premiumOwner), isNull()))
                    .thenReturn(resp);

            mockMvc.perform(get("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isOk());
        }
    }

    // ── DELETE /api/urls/{shortCode} ───────────────────────────────

    @Nested
    @DisplayName("DELETE /api/urls/{shortCode}")
    class DeleteUrl {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous without stats token → 404")
        void anonymousNoToken_returns404() throws Exception {
            doThrow(new UrlNotFoundException(SHORT_CODE))
                    .when(urlShortenerService).deleteUrl(eq(SHORT_CODE), isNull(), isNull());

            mockMvc.perform(delete("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous with valid stats token → 204")
        void anonymousWithToken_returns204() throws Exception {
            doNothing().when(urlShortenerService).deleteUrl(eq(SHORT_CODE), isNull(), eq(STATS_TOKEN));

            mockMvc.perform(delete("/api/urls/{shortCode}", SHORT_CODE)
                            .header("X-Stats-Token", STATS_TOKEN))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free owner → 204")
        void freeOwner_returns204() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));
            doNothing().when(urlShortenerService).deleteUrl(eq(SHORT_CODE), eq(freeOwner), isNull());

            mockMvc.perform(delete("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = OTHER_EMAIL)
        @DisplayName("Premium non-owner → 404")
        void premiumNonOwner_returns404() throws Exception {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(premiumNonOwner));
            doThrow(new UrlNotFoundException(SHORT_CODE))
                    .when(urlShortenerService).deleteUrl(eq(SHORT_CODE), eq(premiumNonOwner), isNull());

            mockMvc.perform(delete("/api/urls/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/analytics/{shortCode} ─────────────────────────────

    @Nested
    @DisplayName("GET /api/analytics/{shortCode}")
    class GetAnalytics {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/analytics/{shortCode}", SHORT_CODE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free owner → 403")
        void freeOwner_returns403() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));
            when(analyticsService.getAnalytics(SHORT_CODE, freeOwner))
                    .thenThrow(new PremiumRequiredException("detailed analytics"));

            mockMvc.perform(get("/api/analytics/{shortCode}", SHORT_CODE))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = OTHER_EMAIL)
        @DisplayName("Premium non-owner → 404")
        void premiumNonOwner_returns404() throws Exception {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(premiumNonOwner));
            when(analyticsService.getAnalytics(SHORT_CODE, premiumNonOwner))
                    .thenThrow(new UrlNotFoundException(SHORT_CODE));

            mockMvc.perform(get("/api/analytics/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium owner → 200")
        void premiumOwner_returns200() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            AnalyticsResponse resp = AnalyticsResponse.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .totalClicks(42)
                    .clicksByDate(Collections.emptyList())
                    .browserStats(Collections.emptyMap())
                    .osStats(Collections.emptyMap())
                    .referrerStats(Collections.emptyMap())
                    .recentClicks(Collections.emptyList())
                    .build();

            when(analyticsService.getAnalytics(SHORT_CODE, premiumOwner)).thenReturn(resp);

            mockMvc.perform(get("/api/analytics/{shortCode}", SHORT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClicks").value(42));
        }
    }

    // ── GET /api/analytics/dashboard ───────────────────────────────

    @Nested
    @DisplayName("GET /api/analytics/dashboard")
    class GetDashboard {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/analytics/dashboard"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free owner → 200 with totals, no ClickEvent fields")
        void freeOwner_returns200NoClickEventFields() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));

            DashboardResponse resp = DashboardResponse.builder()
                    .totalUrls(5)
                    .totalClicks(100)
                    .urlsCreatedToday(1)
                    .topUrls(Collections.emptyList())
                    // Premium fields are null
                    .build();

            when(analyticsService.getDashboardStats(freeOwner)).thenReturn(resp);

            mockMvc.perform(get("/api/analytics/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUrls").value(5))
                    .andExpect(jsonPath("$.totalClicks").value(100))
                    .andExpect(jsonPath("$.clicksByDate").doesNotExist())
                    .andExpect(jsonPath("$.browserStats").doesNotExist())
                    .andExpect(jsonPath("$.osStats").doesNotExist())
                    .andExpect(jsonPath("$.referrerStats").doesNotExist())
                    .andExpect(jsonPath("$.recentClicks").doesNotExist());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium owner → 200 with ClickEvent-derived fields")
        void premiumOwner_returns200WithClickEventFields() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            DashboardResponse resp = DashboardResponse.builder()
                    .totalUrls(5)
                    .totalClicks(100)
                    .urlsCreatedToday(1)
                    .topUrls(Collections.emptyList())
                    .clicksByDate(Collections.emptyList())
                    .browserStats(Collections.emptyMap())
                    .osStats(Collections.emptyMap())
                    .referrerStats(Collections.emptyMap())
                    .recentClicks(Collections.emptyList())
                    .build();

            when(analyticsService.getDashboardStats(premiumOwner)).thenReturn(resp);

            mockMvc.perform(get("/api/analytics/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clicksByDate").isArray())
                    .andExpect(jsonPath("$.browserStats").isMap())
                    .andExpect(jsonPath("$.osStats").isMap());
        }
    }

    // ── GET /api/qr/{shortCode} ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/qr/{shortCode}")
    class GetQrCode {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/qr/{shortCode}", SHORT_CODE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Free owner → 403")
        void freeOwner_returns403() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));

            ShortUrl shortUrl = ShortUrl.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .ownerId(OWNER_ID)
                    .active(true)
                    .build();

            when(shortUrlRepository.findByShortCodeAndActiveTrue(SHORT_CODE))
                    .thenReturn(Optional.of(shortUrl));

            mockMvc.perform(get("/api/qr/{shortCode}", SHORT_CODE))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = OTHER_EMAIL)
        @DisplayName("Premium non-owner → 404")
        void premiumNonOwner_returns404() throws Exception {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(premiumNonOwner));

            ShortUrl shortUrl = ShortUrl.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .ownerId(OWNER_ID) // owned by someone else
                    .active(true)
                    .build();

            when(shortUrlRepository.findByShortCodeAndActiveTrue(SHORT_CODE))
                    .thenReturn(Optional.of(shortUrl));

            mockMvc.perform(get("/api/qr/{shortCode}", SHORT_CODE))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Premium owner → 200 with image/png")
        void premiumOwner_returns200() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(premiumOwner));

            ShortUrl shortUrl = ShortUrl.builder()
                    .shortCode(SHORT_CODE)
                    .originalUrl("https://example.com")
                    .ownerId(OWNER_ID)
                    .active(true)
                    .build();

            when(shortUrlRepository.findByShortCodeAndActiveTrue(SHORT_CODE))
                    .thenReturn(Optional.of(shortUrl));
            when(qrCodeService.generateQrCodePng(any(), eq(300), eq(300)))
                    .thenReturn(new byte[]{1, 2, 3});

            mockMvc.perform(get("/api/qr/{shortCode}", SHORT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG));
        }
    }

    // ── GET /api/me ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/me")
    class GetMe {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Authenticated → 200 with user info")
        void authenticated_returns200() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));

            UserResponse resp = UserResponse.builder()
                    .id(OWNER_ID)
                    .email(OWNER_EMAIL)
                    .plan("FREE")
                    .premium(false)
                    .build();

            when(userService.toUserResponse(freeOwner)).thenReturn(resp);

            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                    .andExpect(jsonPath("$.plan").value("FREE"))
                    .andExpect(jsonPath("$.premium").value(false));
        }
    }

    // ── POST /api/me/upgrade ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/me/upgrade")
    class Upgrade {

        @Test
        @WithAnonymousUser
        @DisplayName("Anonymous → 401")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(post("/api/me/upgrade"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Mock upgrade disabled → 404")
        void mockUpgradeDisabled_returns404() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));
            when(userService.isMockUpgradeEnabled()).thenReturn(false);

            mockMvc.perform(post("/api/me/upgrade"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = OWNER_EMAIL)
        @DisplayName("Mock upgrade enabled → 200 with upgraded user")
        void mockUpgradeEnabled_returns200() throws Exception {
            when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(freeOwner));
            when(userService.isMockUpgradeEnabled()).thenReturn(true);

            User upgraded = User.builder()
                    .id(OWNER_ID)
                    .email(OWNER_EMAIL)
                    .plan(Plan.PREMIUM)
                    .planExpiresAt(LocalDateTime.now().plusDays(30))
                    .build();

            when(userService.upgrade(freeOwner)).thenReturn(upgraded);

            UserResponse resp = UserResponse.builder()
                    .id(OWNER_ID)
                    .email(OWNER_EMAIL)
                    .plan("PREMIUM")
                    .premium(true)
                    .planExpiresAt(upgraded.getPlanExpiresAt())
                    .build();

            when(userService.toUserResponse(upgraded)).thenReturn(resp);

            mockMvc.perform(post("/api/me/upgrade"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan").value("PREMIUM"))
                    .andExpect(jsonPath("$.premium").value(true));
        }
    }

    // ── POST /api/auth/login ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("Rotates the session id so a pre-login JSESSIONID cannot survive")
        void login_rotatesSessionId() throws Exception {
            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken(OWNER_EMAIL, "pw",
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            when(userService.findByEmail(OWNER_EMAIL)).thenReturn(freeOwner);
            when(userService.toUserResponse(freeOwner)).thenReturn(UserResponse.builder()
                    .id(OWNER_ID).email(OWNER_EMAIL).plan("FREE").premium(false).build());

            MockHttpSession planted = new MockHttpSession();
            String plantedId = planted.getId();

            HttpSession after = mockMvc.perform(post("/api/auth/login")
                            .session(planted)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(OWNER_EMAIL, "pw"))))
                    .andExpect(status().isOk())
                    .andReturn().getRequest().getSession();

            assertNotEquals(plantedId, after.getId(), "session id must change on login");
            assertNotNull(after.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        }
    }
}
