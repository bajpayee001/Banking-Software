package com.bank.transaction;

import com.bank.transaction.dto.auth.LoginRequest;
import com.bank.transaction.model.User;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.service.TwoFactorAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class Day4TwoFactorAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TwoFactorAuthService twoFactorAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Checkpoint 4.1: TOTP Generation and RFC 6238 Algorithmic Verification")
    void testTotpGenerationAndVerification() {
        String secret = twoFactorAuthService.generateSecret();
        assertThat(secret).isNotBlank();
        assertThat(secret.length()).isGreaterThanOrEqualTo(16);

        long currentWindow = Instant.now().getEpochSecond() / 30;
        int code = twoFactorAuthService.generateTotpCode(secret, currentWindow);

        // Verification must pass for current time window
        boolean isValid = twoFactorAuthService.verifyCode(secret, code);
        assertThat(isValid).isTrue();

        // Verification must reject wrong code
        int wrongCode = (code + 123456) % 1_000_000;
        boolean isWrongValid = twoFactorAuthService.verifyCode(secret, wrongCode);
        assertThat(isWrongValid).isFalse();
    }

    @Test
    @DisplayName("Checkpoint 4.2: Time Window Tolerance (+/- 1 Step Clock Drift)")
    void testTimeWindowTolerance() {
        String secret = twoFactorAuthService.generateSecret();
        long currentWindow = Instant.now().getEpochSecond() / 30;

        // Code from window -1 (up to 30 seconds ago) must still be accepted
        int codeMinus1 = twoFactorAuthService.generateTotpCode(secret, currentWindow - 1);
        assertThat(twoFactorAuthService.verifyCode(secret, codeMinus1)).isTrue();

        // Code from window +1 (next 30 seconds) must still be accepted
        int codePlus1 = twoFactorAuthService.generateTotpCode(secret, currentWindow + 1);
        assertThat(twoFactorAuthService.verifyCode(secret, codePlus1)).isTrue();

        // Expired code from 5 steps ago (2.5 minutes ago) must be rejected
        int expiredCode = twoFactorAuthService.generateTotpCode(secret, currentWindow - 5);
        assertThat(twoFactorAuthService.verifyCode(secret, expiredCode)).isFalse();
    }

    @Test
    @DisplayName("Checkpoint 4.3: User 2FA Lifecycle (Generate Secret -> Enable -> Validate)")
    void testUser2FaLifecycle() {
        String username = "rahul_sharma";

        // 1. Generate 2FA secret
        String secret = twoFactorAuthService.generate2faSecret(username);
        assertThat(secret).isNotBlank();

        User userAfterSecret = userRepository.findByUsername(username).orElseThrow();
        assertThat(userAfterSecret.getTwoFactorSecret()).isEqualTo(secret);
        assertThat(userAfterSecret.isTwoFactorEnabled()).isFalse();

        // 2. Enable 2FA with valid OTP
        long currentWindow = Instant.now().getEpochSecond() / 30;
        int validCode = twoFactorAuthService.generateTotpCode(secret, currentWindow);

        boolean enabled = twoFactorAuthService.verifyAndEnable2fa(username, validCode);
        assertThat(enabled).isTrue();

        User userAfterEnable = userRepository.findByUsername(username).orElseThrow();
        assertThat(userAfterEnable.isTwoFactorEnabled()).isTrue();

        // 3. Validate OTP
        assertThat(twoFactorAuthService.validateUserOtp(username, validCode)).isTrue();
        assertThat(twoFactorAuthService.validateUserOtp(username, 999999)).isFalse();

        // 4. Disable 2FA with valid OTP
        boolean disabled = twoFactorAuthService.disable2fa(username, validCode);
        assertThat(disabled).isTrue();

        User userAfterDisable = userRepository.findByUsername(username).orElseThrow();
        assertThat(userAfterDisable.isTwoFactorEnabled()).isFalse();
        assertThat(userAfterDisable.getTwoFactorSecret()).isNull();
    }

    @Test
    @DisplayName("Checkpoint 4.4: 2FA REST Endpoints Execution")
    void test2FaRestEndpoints() throws Exception {
        String username = "priya_patel";

        // Step 1: Request 2FA Secret via API
        String responseContent = mockMvc.perform(post("/api/auth/2fa/generate")
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpAuthUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String secret = objectMapper.readTree(responseContent).get("secret").asText();

        // Step 2: Compute valid code and call /api/auth/2fa/enable
        long currentWindow = Instant.now().getEpochSecond() / 30;
        int validCode = twoFactorAuthService.generateTotpCode(secret, currentWindow);

        mockMvc.perform(post("/api/auth/2fa/enable")
                        .param("username", username)
                        .param("code", String.valueOf(validCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));

        // Step 3: Verify OTP code via API
        mockMvc.perform(post("/api/auth/2fa/verify")
                        .param("username", username)
                        .param("code", String.valueOf(validCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // Step 4: Clean up by disabling 2FA
        mockMvc.perform(post("/api/auth/2fa/disable")
                        .param("username", username)
                        .param("code", String.valueOf(validCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorDisabled").value(true));
    }

    @Test
    @DisplayName("Checkpoint 4.5: Role-Secured Endpoints RBAC Enforcement")
    @WithMockUser(username = "regular_user", roles = {"USER"})
    void testUserForbiddenFromAdminEndpoints() throws Exception {
        // Regular user should receive 403 Forbidden on admin system-summary
        mockMvc.perform(get("/api/admin/system-summary"))
                .andExpect(status().isForbidden());

        // Regular user should receive 403 Forbidden on benchmark run
        mockMvc.perform(post("/api/benchmark/run"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Checkpoint 4.6: Admin Permitted on Role-Secured Admin Endpoints")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void testAdminPermittedOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/system-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemStatus").value("OPERATIONAL"));
    }

    @Test
    @DisplayName("Checkpoint 4.7: Unauthenticated Access to Secured API Returns 401/403")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/admin/system-summary"))
                .andExpect(status().isForbidden());
    }
}
