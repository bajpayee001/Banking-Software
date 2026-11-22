package com.bank.transaction.dto.auth;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserProfileResponse {

    private String userId;
    private String username;
    private String email;
    private String role;
    private String customerId;
    private String fullName;
    private String accountNumber;
    private BigDecimal currentBalance;
    private boolean twoFactorEnabled;
    private LocalDateTime memberSince;

    public UserProfileResponse() {}

    public UserProfileResponse(String userId, String username, String email, String role,
                               String customerId, String fullName, String accountNumber,
                               BigDecimal currentBalance, boolean twoFactorEnabled, LocalDateTime memberSince) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.customerId = customerId;
        this.fullName = fullName;
        this.accountNumber = accountNumber;
        this.currentBalance = currentBalance;
        this.twoFactorEnabled = twoFactorEnabled;
        this.memberSince = memberSince;
    }

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

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public LocalDateTime getMemberSince() { return memberSince; }
    public void setMemberSince(LocalDateTime memberSince) { this.memberSince = memberSince; }
}
