package com.santana.carpool.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();

        String provider = determineProvider(authentication);
        String providerId = extractProviderId(attributes, provider);
        String username = extractUsername(attributes, provider);

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existingUser -> {
                    // Update last login
                    User updatedUser = new User(
                            existingUser.id(),
                            existingUser.username(),
                            existingUser.passwordHash(),
                            existingUser.provider(),
                            existingUser.providerId(),
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

        // Generate JWT token for the user
        String token = tokenProvider.generateToken(user.username());

        // Redirect to frontend oauth-callback with token
        response.sendRedirect("http://localhost:4200/oauth-callback?token=" + token);
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
}
