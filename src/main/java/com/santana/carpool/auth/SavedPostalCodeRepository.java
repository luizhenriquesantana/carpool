package com.santana.carpool.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPostalCodeRepository extends MongoRepository<SavedPostalCode, String> {
    List<SavedPostalCode> findByUserId(String userId);
    Optional<SavedPostalCode> findByUserIdAndPostalCode(String userId, String postalCode);
}
