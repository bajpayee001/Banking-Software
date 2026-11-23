package com.bank.transaction.controller;

import com.bank.transaction.dto.auth.*;
import com.bank.transaction.service.AuthService;
import com.bank.transaction.service.TwoFactorAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TwoFactorAuthService twoFactorAuthService;

    public AuthController(AuthService authService, TwoFactorAuthService twoFactorAuthService) {
        this.authService = authService;
        this.twoFactorAuthService = twoFactorAuthService;
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
     * Generates a 2FA TOTP secret key and QR code provisioning URL for an account.
     */
    @PostMapping("/2fa/generate")
    public ResponseEntity<Map<String, String>> generate2faSecret(@RequestParam String username) {
        String secret = twoFactorAuthService.generate2faSecret(username);
        String otpAuthUrl = twoFactorAuthService.getOtpAuthUrl(username, secret, "BankTransactSystem");
        return ResponseEntity.ok(Map.of(
                "username", username,
                "secret", secret,
                "otpAuthUrl", otpAuthUrl,
                "message", "2FA secret generated. Submit code to /api/auth/2fa/enable to activate."
        ));
    }

    /**
     * Verifies OTP code and activates 2FA for the specified user.
     */
    @PostMapping("/2fa/enable")
    public ResponseEntity<Map<String, Object>> enable2fa(@RequestParam String username, @RequestParam int code) {
        boolean enabled = twoFactorAuthService.verifyAndEnable2fa(username, code);
        return ResponseEntity.ok(Map.of(
                "username", username,
                "twoFactorEnabled", enabled,
                "message", enabled ? "2FA activated successfully." : "Invalid OTP code. 2FA not enabled."
        ));
    }

    /**
     * Verifies an OTP code against user's active 2FA secret.
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<Map<String, Object>> verify2fa(@RequestParam String username, @RequestParam int code) {
        boolean valid = twoFactorAuthService.validateUserOtp(username, code);
        return ResponseEntity.ok(Map.of(
                "username", username,
                "valid", valid,
                "message", valid ? "OTP verified successfully." : "Invalid or expired OTP code."
        ));
    }

    /**
     * Disables 2FA after verifying user's OTP code.
     */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Map<String, Object>> disable2fa(@RequestParam String username, @RequestParam int code) {
        boolean disabled = twoFactorAuthService.disable2fa(username, code);
        return ResponseEntity.ok(Map.of(
                "username", username,
                "twoFactorDisabled", disabled,
                "message", disabled ? "2FA disabled successfully." : "Invalid OTP code. Cannot disable 2FA."
        ));
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
