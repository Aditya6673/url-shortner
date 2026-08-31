package com.urlshortner.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnonymousRateLimitFilter extends OncePerRequestFilter {

    // ponytail: single-instance only, swap for Bucket4j or Redis when more than one node runs
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    // ponytail: bounded by dropping last-hour entries once the map gets big. A flood of
    // >MAX_TRACKED_IPS distinct IPs inside one hour still grows it — that needs Redis.
    private static final int MAX_TRACKED_IPS = 10_000;

    @Value("${app.rate-limit.anonymous-links-per-hour}")
    private int maxRequestsPerHour;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Only rate-limit POST /api/urls
        if (!"POST".equalsIgnoreCase(request.getMethod())
            || !"/api/urls".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip rate limiting for authenticated users
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        long currentHour = System.currentTimeMillis() / 3_600_000;

        if (windows.size() > MAX_TRACKED_IPS) {
            windows.values().removeIf(w -> w[0] != currentHour);
        }

        long[] window = windows.compute(ip, (k, v) -> {
            if (v == null || v[0] != currentHour) {
                return new long[]{currentHour, 1};
            }
            v[1]++;
            return v;
        });

        if (window[1] > maxRequestsPerHour) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
