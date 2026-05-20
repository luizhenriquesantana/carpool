package com.santana.carpool.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        return Map.of("message", "Logged out successfully");
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
