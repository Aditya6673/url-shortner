package com.urlshortner.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: static assets and root page
                .requestMatchers(HttpMethod.GET, "/", "/index.html",
                    "/css/**", "/js/**", "/favicon.ico").permitAll()
                // Public: auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                // Public: URL creation (anonymous allowed, rate-limited separately)
                .requestMatchers(HttpMethod.POST, "/api/urls").permitAll()
                // Public: redirect
                .requestMatchers(HttpMethod.GET, "/{shortCode:[a-zA-Z0-9-]+}").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf
                // ponytail: token-less CSRF defence — the session cookie is SameSite=Lax
                // (see application.yml), so no cross-site POST/DELETE ever carries it.
                // Add CookieCsrfTokenRepository + an X-XSRF-TOKEN header in app.js if this
                // ever needs to survive a sibling subdomain or a pre-SameSite browser.
                .ignoringRequestMatchers("/api/**")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_OK))
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
