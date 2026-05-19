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

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        userRepository.findByProviderAndProviderId(provider, providerId)
                .ifPresentOrElse(
                        user -> {
                            // Update last login
                            User updatedUser = new User(
                                    user.id(),
                                    user.username(),
                                    user.passwordHash(),
                                    user.provider(),
                                    user.providerId(),
                                    user.userRegion(),
                                    user.createDate(),
                                    LocalDateTime.now(),
                                    LocalDateTime.now()
                            );
                            userRepository.save(updatedUser);
                        },
                        () -> {
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
                            userRepository.save(newUser);
                        }
                );

        // Redirect to frontend or return success
        response.sendRedirect("/");
    }

    private String determineProvider(Authentication authentication) {
        // Determine provider from the authentication object
        // This will be set by Spring Security OAuth2
        String registrationId = authentication.getAuthorities().stream()
                .filter(auth -> auth.getAuthority().startsWith("OAUTH2_USER_"))
                .map(auth -> auth.getAuthority().substring("OAUTH2_USER_".length()))
                .findFirst()
                .orElse("unknown");
        return registrationId.toLowerCase();
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
