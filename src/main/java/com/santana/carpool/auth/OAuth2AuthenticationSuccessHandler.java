package com.santana.carpool.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
    
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    
    @Value("${frontend.redirect-url}")
    private String frontendRedirectUrl;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oauth2User.getAttributes();

            logger.info("OAuth2 authentication success. Attributes keys: {}", attributes.keySet());

            String provider = determineProvider(authentication);
            logger.info("Determined provider: {}", provider);

            String providerId = extractProviderId(attributes, provider);
            String rawEmail = extractEmail(attributes, provider);
            
            if (rawEmail == null || rawEmail.isBlank()) {
                logger.error("Email not found in OAuth2 attributes for provider: {}", provider);
                logger.error("Available attributes: {}", attributes.keySet());
                throw new IllegalArgumentException("Email is required but not provided by OAuth2 provider: " + provider);
            }
            
            String email = rawEmail.toLowerCase();
            String username = extractUsername(attributes, provider).toLowerCase();

            logger.info("Creating/updating user: provider={}, providerId={}, email={}, username={}", provider, providerId, email, username);

            User user = userRepository.findByEmail(email)
                    .map(existingUser -> {
                        // Update last login and provider info if different
                        User updatedUser = new User(
                                existingUser.id(),
                                existingUser.email(),
                                username,
                                existingUser.passwordHash(),
                                provider,
                                providerId,
                                existingUser.userRegion(),
                                existingUser.createDate(),
                                LocalDateTime.now(),
                                LocalDateTime.now()
                        );
                        return userRepository.save(updatedUser);
                    })
                    .orElseGet(() -> {
                        // Create new user
                        User newUser = new User(
                                null,
                                email,
                                username,
                                null,
                                provider,
                                providerId,
                                null,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                LocalDateTime.now()
                        );
                        return userRepository.save(newUser);
                    });

            logger.info("User created/updated successfully: {}", user.email());

            // Generate JWT token for the user
            String token = tokenProvider.generateToken(user.email());
            logger.info("JWT token generated successfully");

            // Redirect to frontend oauth-callback with token
            String redirectUrl = frontendRedirectUrl + "?token=" + token;
            logger.info("Redirecting to: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            logger.error("Error during OAuth2 authentication success handling", e);
            throw e;
        }
    }

    private String determineProvider(Authentication authentication) {
        // Extract provider from the OAuth2AuthorizedClient or from request
        // For simplicity, we'll check if it's a Google or GitHub OAuth2User
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();
        
        // Google uses "sub" as the unique identifier
        // GitHub uses "id" as the unique identifier
        if (attributes.containsKey("sub")) {
            return "google";
        } else if (attributes.containsKey("id") && attributes.containsKey("login")) {
            return "github";
        }
        
        return "unknown";
    }

    private String extractProviderId(Map<String, Object> attributes, String provider) {
        return switch (provider) {
            case "google" -> (String) attributes.get("sub");
            case "github" -> String.valueOf(attributes.get("id"));
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private String extractUsername(Map<String, Object> attributes, String provider) {
        return switch (provider) {
            case "google" -> (String) attributes.get("name");
            case "github" -> (String) attributes.get("login");
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private String extractEmail(Map<String, Object> attributes, String provider) {
        return switch (provider) {
            case "google" -> (String) attributes.get("email");
            case "github" -> (String) attributes.get("email");
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }
}
