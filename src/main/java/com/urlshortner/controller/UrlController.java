package com.urlshortner.controller;

import com.urlshortner.document.User;
import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.repository.UserRepository;
import com.urlshortner.service.UrlShortenerService;
import com.urlshortner.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlShortenerService urlShortenerService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        // user is nullable — anonymous callers get a statsToken
        User user = SecurityUtils.getCurrentUser(userRepository).orElse(null);
        UrlResponse response = urlShortenerService.createShortUrl(request, user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<UrlResponse>> getAllUrls() {
        // SecurityConfig enforces authentication for this endpoint
        User user = SecurityUtils.requireCurrentUser(userRepository);
        return ResponseEntity.ok(urlShortenerService.getAllUrls(user.getId(), user.isPremium()));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> getUrlByShortCode(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Stats-Token", required = false) String statsToken) {
        User user = SecurityUtils.getCurrentUser(userRepository).orElse(null);
        return ResponseEntity.ok(urlShortenerService.getUrlByShortCode(shortCode, user, statsToken));
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Stats-Token", required = false) String statsToken) {
        User user = SecurityUtils.getCurrentUser(userRepository).orElse(null);
        urlShortenerService.deleteUrl(shortCode, user, statsToken);
        return ResponseEntity.noContent().build();
    }
}
