package com.urlshortner.util;

import com.urlshortner.document.ShortUrl;
import com.urlshortner.document.User;
import com.urlshortner.exception.PremiumRequiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
        // utility class
    }

    public static Optional<User> getCurrentUser(UserRepository userRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(auth.getName());
    }

    /** For endpoints SecurityConfig already marks authenticated() — no user here means a config bug. */
    public static User requireCurrentUser(UserRepository userRepository) {
        return getCurrentUser(userRepository)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * Ownership first, then plan: a non-owner gets 404 so they never learn the link exists,
     * and only then does a free owner get told this is a premium feature.
     */
    public static void requireOwnerAndPremium(ShortUrl shortUrl, User user, String feature) {
        if (shortUrl.getOwnerId() == null || !shortUrl.getOwnerId().equals(user.getId())) {
            throw new UrlNotFoundException(shortUrl.getShortCode());
        }
        if (!user.isPremium()) {
            throw new PremiumRequiredException(feature);
        }
    }
}
