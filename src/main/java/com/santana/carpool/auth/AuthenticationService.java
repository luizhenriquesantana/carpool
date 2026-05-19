package com.santana.carpool.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RestTemplate restTemplate;
    private static final ZoneId LHR_ZONE = ZoneId.of("Europe/London");

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtTokenProvider tokenProvider,
                                 RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.restTemplate = restTemplate;
    }

    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String username = canonicalize(request.username());
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        LocalDateTime now = LocalDateTime.now(LHR_ZONE);
        String region = extractRegionFromRequest(httpRequest);
        User user = new User(null, username, passwordHash, "local", null, region, now, now, null);
        userRepository.save(user);
        return new AuthResponse(tokenProvider.generateToken(username));
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = canonicalize(request.username());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        LocalDateTime now = LocalDateTime.now(LHR_ZONE);
        String region = extractRegionFromRequest(httpRequest);
        User updatedUser = new User(user.id(), user.username(), user.passwordHash(), user.provider(),
                user.providerId(), region != null ? region : user.userRegion(), user.createDate(), now, now);
        userRepository.save(updatedUser);

        return new AuthResponse(tokenProvider.generateToken(username));
    }

    private String extractRegionFromRequest(HttpServletRequest request) {
        String ip = getClientIpAddress(request);
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip) || "127.0.0.1".equals(ip) || "::1".equals(ip)) {
            return null;
        }

        try {
            String url = "http://ip-api.com/json/" + ip + "?fields=country";
            @SuppressWarnings("unchecked")
            Map<String, String> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("country")) {
                return response.get("country");
            }
        } catch (Exception e) {
            // Fail silently - geolocation is optional
        }

        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String canonicalize(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.toLowerCase(Locale.ROOT).trim();
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(String token) {
    }
}
