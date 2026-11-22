package com.bank.transaction.controller;

import com.bank.transaction.dto.auth.*;
import com.bank.transaction.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates User or Admin and returns JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Registers a new regular user, creates customer profile and bank account.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        AuthResponse response = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Provisions a new administrator account using master admin key.
     */
    @PostMapping("/create-admin")
    public ResponseEntity<AuthResponse> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        AuthResponse response = authService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Fetches current authenticated user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileResponse profile = authService.getCurrentUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }
}
