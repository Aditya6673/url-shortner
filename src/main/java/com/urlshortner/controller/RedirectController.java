package com.urlshortner.controller;

import com.urlshortner.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    @GetMapping("/{shortCode:^[a-zA-Z0-9-]+$}")
    public String redirect(@PathVariable String shortCode, HttpServletRequest request) {
        if (shortCode.equals("api") || shortCode.equals("css") || shortCode.equals("js") || shortCode.equals("favicon.ico")) {
            return "forward:/" + shortCode;
        }
        String originalUrl = urlShortenerService.resolveAndTrack(shortCode, request);
        return "redirect:" + originalUrl;
    }
}
