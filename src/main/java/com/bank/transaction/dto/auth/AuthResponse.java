package com.bank.transaction.dto.auth;

import java.math.BigDecimal;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private String userId;
    private String username;
    private String email;
    private String role;
    private String customerId;
    private String accountNumber;
    private BigDecimal balance;
    private long expiresInSeconds;

    public AuthResponse() {}

    public AuthResponse(String token, String userId, String username, String email,
                        String role, String customerId, String accountNumber,
                        BigDecimal balance, long expiresInSeconds) {
        this.token = token;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
