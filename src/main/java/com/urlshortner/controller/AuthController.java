package com.urlshortner.controller;

import com.urlshortner.document.User;
import com.urlshortner.dto.LoginRequest;
import com.urlshortner.dto.RegisterRequest;
import com.urlshortner.dto.UserResponse;
import com.urlshortner.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        User user = userService.register(request);

        // Auto-login after registration
        authenticateAndCreateSession(request.getEmail(), request.getPassword(), httpRequest);

        return new ResponseEntity<>(userService.toUserResponse(user), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        authenticateAndCreateSession(request.getEmail(), request.getPassword(), httpRequest);

        User user = userService.findByEmail(request.getEmail());
        return ResponseEntity.ok(userService.toUserResponse(user));
    }

    // Logout is handled by Spring Security's logout filter at /api/auth/logout

    private void authenticateAndCreateSession(String email, String password,
                                               HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.toLowerCase().trim(), password));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Rotate the session id: a JSESSIONID that existed before login must not
        // survive it (session fixation). Logging in from a custom controller skips
        // Spring Security's SessionAuthenticationStrategy, which would normally do this.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
