package com.urlshortner.controller;

import com.urlshortner.dto.CreateUrlRequest;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.service.UrlShortenerService;
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

    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlShortenerService.createShortUrl(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<UrlResponse>> getAllUrls() {
        return ResponseEntity.ok(urlShortenerService.getAllUrls());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UrlResponse> getUrlById(@PathVariable String id) {
        // Technically mapping id to shortCode in the method parameter here, though the prompt implies using shortCode. Let's use id.
        return ResponseEntity.ok(urlShortenerService.getUrlByShortCode(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String id) {
        urlShortenerService.deleteUrl(id);
        return ResponseEntity.noContent().build();
    }
}
