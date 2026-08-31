package com.urlshortner.controller;

import com.urlshortner.document.User;
import com.urlshortner.dto.UserResponse;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.UserRepository;
import com.urlshortner.service.UserService;
import com.urlshortner.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserResponse> getCurrentUser() {
        User user = SecurityUtils.requireCurrentUser(userRepository);
        return ResponseEntity.ok(userService.toUserResponse(user));
    }

    @PostMapping("/upgrade")
    public ResponseEntity<UserResponse> upgrade() {
        if (!userService.isMockUpgradeEnabled()) {
            // Return 404 when mock upgrade is disabled, per spec:
            // "it cannot ship enabled"
            throw new UrlNotFoundException("upgrade");
        }

        User upgraded = userService.upgrade(SecurityUtils.requireCurrentUser(userRepository));
        return ResponseEntity.ok(userService.toUserResponse(upgraded));
    }
}
