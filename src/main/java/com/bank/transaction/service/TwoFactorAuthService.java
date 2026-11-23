package com.bank.transaction.service;

import com.bank.transaction.model.User;
import com.bank.transaction.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Service providing RFC 6238 compliant Time-based One-Time Password (TOTP)
 * for Two-Factor Authentication (2FA).
 */
@Service
public class TwoFactorAuthService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorAuthService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int DIGITS_MODULO = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Generates a cryptographically secure 16-byte Base32-encoded secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[20];
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Generates and stores a new 2FA secret for the user, pending OTP verification.
     */
    @Transactional
    public String generate2faSecret(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String secret = generateSecret();
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        log.info("Generated new 2FA secret for user: {}", username);
        return secret;
    }

    /**
     * Generates Google Authenticator compatible QR Code provisioning URI.
     */
    public String getOtpAuthUrl(String username, String secret, String issuer) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, username, secret, issuer, DIGITS, TIME_STEP_SECONDS);
    }

    /**
     * Verifies the OTP code for the given secret within a +/- 1 step window (tolerance for clock drift).
     */
    public boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isBlank()) {
            return false;
        }

        long currentWindow = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        // Check current window and +/- 1 window for clock drift
        for (int i = -1; i <= 1; i++) {
            long window = currentWindow + i;
            int generated = generateTotpCode(secret, window);
            if (generated == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies an OTP code and activates 2FA for the user.
     */
    @Transactional
    public boolean verifyAndEnable2fa(String username, int code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (user.getTwoFactorSecret() == null) {
            throw new BadCredentialsException("2FA setup not initiated. Generate secret first.");
        }

        boolean valid = verifyCode(user.getTwoFactorSecret(), code);
        if (valid) {
            user.setTwoFactorEnabled(true);
            userRepository.save(user);
            log.info("2FA successfully enabled for user: {}", username);
            return true;
        } else {
            log.warn("Invalid 2FA verification attempt for user: {}", username);
            return false;
        }
    }

    /**
     * Validates 2FA code during login or high-security operation.
     */
    @Transactional(readOnly = true)
    public boolean validateUserOtp(String username, int code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isTwoFactorEnabled()) {
            return true; // 2FA is not required for this user
        }

        return verifyCode(user.getTwoFactorSecret(), code);
    }

    /**
     * Disables 2FA after validating user's current OTP code.
     */
    @Transactional
    public boolean disable2fa(String username, int code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isTwoFactorEnabled()) {
            return true;
        }

        if (verifyCode(user.getTwoFactorSecret(), code)) {
            user.setTwoFactorEnabled(false);
            user.setTwoFactorSecret(null);
            userRepository.save(user);
            log.info("2FA disabled for user: {}", username);
            return true;
        }
        return false;
    }

    /**
     * Computes the 6-digit TOTP code for a specific time window.
     */
    public int generateTotpCode(String base32Secret, long timeWindow) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation per RFC 4226 / 6238
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return binary % DIGITS_MODULO;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate TOTP code", e);
        }
    }

    // --- Base32 Encoding / Decoding Utilities ---

    public static String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                int index = (buffer >> bitsLeft) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }

        return result.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer byteBuffer = ByteBuffer.allocate(clean.length() * 5 / 8);

        int buffer = 0;
        int bitsLeft = 0;

        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) continue;

            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                byteBuffer.put((byte) ((buffer >> bitsLeft) & 0xFF));
            }
        }

        byteBuffer.flip();
        byte[] result = new byte[byteBuffer.remaining()];
        byteBuffer.get(result);
        return result;
    }
}
