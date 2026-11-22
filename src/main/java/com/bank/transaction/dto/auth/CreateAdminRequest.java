package com.bank.transaction.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAdminRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Admin master secret key is required")
    private String adminSecretKey;

    public CreateAdminRequest() {}

    public CreateAdminRequest(String username, String email, String password, String adminSecretKey) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.adminSecretKey = adminSecretKey;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAdminSecretKey() { return adminSecretKey; }
    public void setAdminSecretKey(String adminSecretKey) { this.adminSecretKey = adminSecretKey; }
}
