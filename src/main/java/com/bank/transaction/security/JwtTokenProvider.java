package com.bank.transaction.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard RFC 7519 JSON Web Token (JWT) provider utilizing HMAC-SHA256.
 * Issues, parses, and validates stateless bearer tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final String secretKey;
    private final long validityInSeconds;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${banking.jwt.secret:BankSecureSecretKeyForJwtAuthenticationTokens2026!}") String secretKey,
            @Value("${banking.jwt.validity-seconds:86400}") long validityInSeconds) {
        this.secretKey = secretKey;
        this.validityInSeconds = validityInSeconds;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates a signed JWT token containing user identity and role claims.
     */
    public String generateToken(String username, String userId, String role, String customerId) {
        try {
            long nowSeconds = Instant.now().getEpochSecond();
            long expSeconds = nowSeconds + validityInSeconds;

            // 1. Header
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String encodedHeader = base64UrlEncode(objectMapper.writeValueAsBytes(header));

            // 2. Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", username);
            payload.put("userId", userId);
            payload.put("role", role);
            if (customerId != null) {
                payload.put("customerId", customerId);
            }
            payload.put("iat", nowSeconds);
            payload.put("exp", expSeconds);
            String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payload));

            // 3. Signature
            String signature = sign(encodedHeader + "." + encodedPayload);

            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception e) {
            log.error("Failed to generate JWT token for user {}: {}", username, e.getMessage());
            throw new RuntimeException("Could not generate authentication token", e);
        }
    }

    /**
     * Validates the cryptographic signature and expiration of the JWT token.
     */
    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return false;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String content = parts[0] + "." + parts[1];
            String expectedSignature = sign(content);
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Invalid JWT signature");
                return false;
            }

            // Verify expiration
            JsonNode payloadNode = parsePayload(parts[1]);
            long exp = payloadNode.path("exp").asLong(0);
            if (exp > 0 && Instant.now().getEpochSecond() > exp) {
                log.warn("JWT token has expired");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts username (sub claim) from token.
     */
    public String extractUsername(String token) {
        try {
            String[] parts = token.split("\\.");
            JsonNode payload = parsePayload(parts[1]);
            return payload.path("sub").asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts role claim from token.
     */
    public String extractRole(String token) {
        try {
            String[] parts = token.split("\\.");
            JsonNode payload = parsePayload(parts[1]);
            return payload.path("role").asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts userId claim from token.
     */
    public String extractUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            JsonNode payload = parsePayload(parts[1]);
            return payload.path("userId").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parsePayload(String encodedPayload) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(encodedPayload);
        return objectMapper.readTree(bytes);
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(hash);
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
