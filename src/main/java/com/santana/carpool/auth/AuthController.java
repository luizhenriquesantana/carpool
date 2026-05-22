package com.santana.carpool.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    public AuthController(AuthenticationService authenticationService, UserRepository userRepository) {
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticationService.AuthResponse register(@RequestBody AuthenticationService.RegisterRequest request,
                                                       HttpServletRequest httpRequest) {
        return authenticationService.register(request, httpRequest);
    }

    @PostMapping("/login")
    public AuthenticationService.AuthResponse login(@RequestBody AuthenticationService.LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        return authenticationService.login(request, httpRequest);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        // Clear security context
        SecurityContextHolder.clearContext();
        
        // Invalidate session completely to prevent OAuth2 state interference
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        
        return Map.of("message", "Logged out successfully");
    }

    @GetMapping("/profile")
    public Map<String, Object> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return Map.of(
                "email", user.email(),
                "displayName", user.username() != null ? user.username() : user.email(),
                "provider", user.provider(),
                "memberSince", user.createDate() != null ? user.createDate().toString() : "",
                "lastLogin", user.lastLogin() != null ? user.lastLogin().toString() : ""
        );
    }

    @PutMapping("/profile")
    public Map<String, String> updateProfile(@RequestBody Map<String, String> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String displayName = request.get("displayName");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required.");
        }

        User updatedUser = new User(
                user.id(),
                user.email(),
                displayName,
                user.passwordHash(),
                user.provider(),
                user.providerId(),
                user.userRegion(),
                user.createDate(),
                LocalDateTime.now(),
                user.lastLogin()
        );
        userRepository.save(updatedUser);
        return Map.of("message", "Profile updated successfully");
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleBadCredentials(BadCredentialsException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
