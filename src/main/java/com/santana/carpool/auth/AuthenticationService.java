package com.santana.carpool.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = canonicalize(request.username());
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(null, username, passwordHash, "local", null);
        userRepository.save(user);
        return new AuthResponse(tokenProvider.generateToken(username));
    }

    public AuthResponse login(LoginRequest request) {
        String username = canonicalize(request.username());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        return new AuthResponse(tokenProvider.generateToken(username));
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
