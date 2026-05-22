package com.santana.carpool.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SavedPostalCodeService {
    private final SavedPostalCodeRepository savedPostalCodeRepository;
    private final UserRepository userRepository;

    public SavedPostalCodeService(SavedPostalCodeRepository savedPostalCodeRepository, UserRepository userRepository) {
        this.savedPostalCodeRepository = savedPostalCodeRepository;
        this.userRepository = userRepository;
    }

    public List<SavedPostalCode> listForUser(String email) {
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."))
                .id();
        return savedPostalCodeRepository.findByUserId(userId);
    }

    public SavedPostalCode saveForUser(String email, SavedPostalCodeRequest request) {
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."))
                .id();
        Instant now = Instant.now();
        SavedPostalCode savedPostalCode = new SavedPostalCode(
                null,
                userId,
                request.label(),
                request.postalCode(),
                request.country(),
                now,
                now
        );
        return savedPostalCodeRepository.save(savedPostalCode);
    }

    public void touchLastUsedForUser(String email, String postalCode) {
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."))
                .id();
        Optional<SavedPostalCode> existing = savedPostalCodeRepository.findByUserIdAndPostalCode(userId, postalCode);
        if (existing.isPresent()) {
            SavedPostalCode old = existing.get();
            SavedPostalCode updated = new SavedPostalCode(
                    old.id(),
                    old.userId(),
                    old.label(),
                    old.postalCode(),
                    old.country(),
                    old.createdAt(),
                    Instant.now()
            );
            savedPostalCodeRepository.save(updated);
        }
    }

    public void deleteForUser(String email, String id) {
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."))
                .id();
        SavedPostalCode postalCode = savedPostalCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Saved postal code not found."));
        if (!postalCode.userId().equals(userId)) {
            throw new IllegalArgumentException("Cannot delete postal code for another user.");
        }
        savedPostalCodeRepository.delete(postalCode);
    }

    public record SavedPostalCodeRequest(String label, String postalCode, String country) {
    }
}
