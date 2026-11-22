package com.bank.transaction;

import com.bank.transaction.dto.auth.LoginRequest;
import com.bank.transaction.dto.auth.RegisterUserRequest;
import com.bank.transaction.security.JwtTokenProvider;
import com.bank.transaction.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class Day3SecurityEdgeCasesTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Edge Case 1: Duplicate Username Registration Must Fail")
    void testDuplicateUsernameRegistrationFails() {
        RegisterUserRequest duplicateUser = new RegisterUserRequest(
                "rahul_sharma", // Already seeded!
                "new.unique.email@banktest.com",
                "Pass@12345",
                "Rahul",
                "Sharma",
                "+91-9988112233",
                new BigDecimal("1000.00")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            authService.registerUser(duplicateUser);
        });
        assertThat(ex.getMessage()).contains("already taken");
    }

    @Test
    @DisplayName("Edge Case 2: Duplicate Email Registration Must Fail")
    void testDuplicateEmailRegistrationFails() {
        RegisterUserRequest duplicateEmail = new RegisterUserRequest(
                "brand_new_user",
                "rahul.sharma@example.com", // Already seeded!
                "Pass@12345",
                "Rahul",
                "New",
                "+91-9988112233",
                new BigDecimal("1000.00")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            authService.registerUser(duplicateEmail);
        });
        assertThat(ex.getMessage()).contains("already registered");
    }

    @Test
    @DisplayName("Edge Case 3: Non-Existent User Login Must Fail")
    void testNonExistentUserLoginFails() {
        LoginRequest ghostRequest = new LoginRequest("non_existent_ghost_user_999", "AnyPassword123");
        assertThrows(BadCredentialsException.class, () -> authService.login(ghostRequest));
    }

    @Test
    @DisplayName("Edge Case 4: Cryptographically Tampered JWT Token Must Be Rejected")
    void testTamperedJwtTokenRejected() {
        String validToken = jwtTokenProvider.generateToken("admin", "USER-ADMIN-01", "ROLE_ADMIN", null);
        assertThat(jwtTokenProvider.validateToken(validToken)).isTrue();

        // Tamper with the signature portion
        String tamperedToken = validToken.substring(0, validToken.length() - 4) + "XXXX";
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        assertThat(jwtTokenProvider.extractUsername(tamperedToken)).isNotNull(); // Payload legible but untrusted!
    }

    @Test
    @DisplayName("Edge Case 5: Malformed Token Structures (Null, Empty, Partial) Must Be Rejected")
    void testMalformedTokensRejected() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("   ")).isFalse();
        assertThat(jwtTokenProvider.validateToken("header.payload")).isFalse(); // Missing signature
        assertThat(jwtTokenProvider.validateToken("onlyonepart")).isFalse();
        assertThat(jwtTokenProvider.validateToken("part1.part2.part3.part4")).isFalse(); // Too many parts
    }

    @Test
    @DisplayName("Edge Case 6: Login via Email (Alternative to Username) Must Succeed")
    void testLoginWithEmailSucceeds() {
        LoginRequest emailLogin = new LoginRequest("rahul.sharma@example.com", "User@123");
        var response = authService.login(emailLogin);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("rahul_sharma");
        assertThat(response.getToken()).isNotBlank();
    }
}
