package com.urlshortner.controller;

import com.google.zxing.WriterException;
import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.ShortUrlRepository;
import com.urlshortner.repository.UserRepository;
import com.urlshortner.service.QrCodeService;
import com.urlshortner.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrCodeController {

    private final QrCodeService qrCodeService;
    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @GetMapping(value = "/{shortCode}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQrCode(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "300") int height) throws WriterException, IOException {

        // Premium + owner gate
        User user = SecurityUtils.getCurrentUser(userRepository)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        SecurityUtils.requireOwnerAndPremium(shortUrl, user, "QR codes");

        String content = baseUrl + "/" + shortCode;
        byte[] qrCode = qrCodeService.generateQrCodePng(content, width, height);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCode);
    }
}
