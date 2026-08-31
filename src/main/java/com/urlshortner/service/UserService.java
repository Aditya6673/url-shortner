package com.urlshortner.service;

import com.urlshortner.document.Plan;
import com.urlshortner.document.User;
import com.urlshortner.dto.RegisterRequest;
import com.urlshortner.dto.UserResponse;
import com.urlshortner.exception.EmailAlreadyExistsException;
import com.urlshortner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.billing.mock-upgrade-enabled}")
    private boolean mockUpgradeEnabled;

    public User register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim()).orElse(null);
    }

    public User upgrade(User user) {
        user.setPlan(Plan.PREMIUM);
        user.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
        return userRepository.save(user);
    }

    public boolean isMockUpgradeEnabled() {
        return mockUpgradeEnabled;
    }

    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .plan(user.getPlan().name())
                .premium(user.isPremium())
                .planExpiresAt(user.getPlanExpiresAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
