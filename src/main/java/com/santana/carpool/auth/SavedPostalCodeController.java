package com.santana.carpool.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class SavedPostalCodeController {
    private final SavedPostalCodeService postalCodeService;

    public SavedPostalCodeController(SavedPostalCodeService postalCodeService) {
        this.postalCodeService = postalCodeService;
    }

    @GetMapping("/saved-postal-codes")
    public List<SavedPostalCodeView> listSavedPostalCodes(Authentication authentication) {
        return postalCodeService.listForUser(authentication.getName()).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @PostMapping("/saved-postal-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedPostalCodeView createSavedPostalCode(
            Authentication authentication,
            @RequestBody SavedPostalCodeService.SavedPostalCodeRequest request
    ) {
        return toView(postalCodeService.saveForUser(authentication.getName(), request));
    }

    @DeleteMapping("/saved-postal-codes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedPostalCode(Authentication authentication, @PathVariable String id) {
        postalCodeService.deleteForUser(authentication.getName(), id);
    }

    private SavedPostalCodeView toView(SavedPostalCode savedPostalCode) {
        return new SavedPostalCodeView(
                savedPostalCode.id(),
                savedPostalCode.label(),
                savedPostalCode.postalCode(),
                savedPostalCode.country()
        );
    }

    public record SavedPostalCodeView(String id, String label, String postalCode, String country) {
    }
}
