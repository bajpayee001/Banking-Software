package com.bank.transaction;

import com.bank.transaction.dto.auth.AuthResponse;
import com.bank.transaction.dto.auth.CreateAdminRequest;
import com.bank.transaction.dto.auth.LoginRequest;
import com.bank.transaction.dto.auth.RegisterUserRequest;
import com.bank.transaction.model.Role;
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
public class Day3AuthSecurityTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Checkpoint 3.1: Admin Login Returns Valid JWT with ROLE_ADMIN")
    void testAdminLoginSuccess() {
        LoginRequest request = new LoginRequest("admin", "Admin@123");
        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRole()).isEqualTo(Role.ROLE_ADMIN.name());
        assertThat(response.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Checkpoint 3.2: Customer User Login Returns Valid JWT with ROLE_USER")
    void testUserLoginSuccess() {
        LoginRequest request = new LoginRequest("rahul_sharma", "User@123");
        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRole()).isEqualTo(Role.ROLE_USER.name());
        assertThat(response.getCustomerId()).isEqualTo("CUST101");
        assertThat(response.getAccountNumber()).isNotBlank();
    }

    @Test
    @DisplayName("Checkpoint 3.3: Invalid Password Rejection")
    void testInvalidPasswordFails() {
        LoginRequest badRequest = new LoginRequest("admin", "WrongPassword999");
        assertThrows(BadCredentialsException.class, () -> authService.login(badRequest));
    }

    @Test
    @DisplayName("Checkpoint 3.4: Dynamic Customer User Registration")
    void testUserRegistration() {
        RegisterUserRequest regRequest = new RegisterUserRequest(
                "suresh_raina",
                "suresh.raina@cricketbank.com",
                "Pass@12345",
                "Suresh",
                "Raina",
                "+91-9988112233",
                new BigDecimal("15000.00")
        );

        AuthResponse regResponse = authService.registerUser(regRequest);
        assertThat(regResponse).isNotNull();
        assertThat(regResponse.getUsername()).isEqualTo("suresh_raina");
        assertThat(regResponse.getRole()).isEqualTo(Role.ROLE_USER.name());
        assertThat(regResponse.getBalance()).isEqualByComparingTo("15000.00");
        assertThat(regResponse.getAccountNumber()).startsWith("SB-");
    }

    @Test
    @DisplayName("Checkpoint 3.5: Dynamic Admin Provisioning with Master Key")
    void testAdminCreation() {
        CreateAdminRequest validReq = new CreateAdminRequest(
                "super_admin_2",
                "super2@banktest.com",
                "AdminKey@2026",
                "BANK_ADMIN_MASTER_KEY_2026"
        );

        AuthResponse adminResp = authService.createAdmin(validReq);
        assertThat(adminResp).isNotNull();
        assertThat(adminResp.getRole()).isEqualTo(Role.ROLE_ADMIN.name());

        // Unauthorized key test
        CreateAdminRequest invalidReq = new CreateAdminRequest(
                "fake_admin",
                "fake@banktest.com",
                "AdminKey@2026",
                "INVALID_SECRET"
        );
        assertThrows(BadCredentialsException.class, () -> authService.createAdmin(invalidReq));
    }
}
