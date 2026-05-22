package com.santana.carpool.auth;

import org.springframework.stereotype.Service;

import java.util.List;

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
        SavedPostalCode savedPostalCode = new SavedPostalCode(
                null,
                userId,
                request.label(),
                request.postalCode(),
                request.country()
        );
        return savedPostalCodeRepository.save(savedPostalCode);
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
