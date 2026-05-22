package com.santana.carpool.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
public record User(
        @Id
        String id,
        @Indexed(unique = true)
        String email,
        String username,
        String passwordHash,
        String provider,
        String providerId,
        String userRegion,
        LocalDateTime createDate,
        LocalDateTime updateDate,
        LocalDateTime lastLogin
) {
    public User {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        if (provider.equals("local") && (passwordHash == null || passwordHash.isBlank())) {
            throw new IllegalArgumentException("Password hash is required for local users.");
        }
    }
}
