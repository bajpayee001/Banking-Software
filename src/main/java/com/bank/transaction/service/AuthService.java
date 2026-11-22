package com.bank.transaction.service;

import com.bank.transaction.dto.auth.*;
import com.bank.transaction.model.*;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.CustomerRepository;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final String adminSecretKey;

    public AuthService(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Value("${banking.admin.secret-key:BANK_ADMIN_MASTER_KEY_2026}") String adminSecretKey) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminSecretKey = adminSecretKey;
    }

    @jakarta.annotation.PostConstruct
    public void initDefaultPasswords() {
        userRepository.findByUsername("admin").ifPresent(user -> {
            user.setPassword(passwordEncoder.encode("Admin@123"));
            userRepository.save(user);
        });
        userRepository.findByUsername("rahul_sharma").ifPresent(user -> {
            user.setPassword(passwordEncoder.encode("User@123"));
            userRepository.save(user);
        });
        userRepository.findByUsername("priya_patel").ifPresent(user -> {
            user.setPassword(passwordEncoder.encode("User@123"));
            userRepository.save(user);
        });
    }

    /**
     * Authenticates User or Admin credentials and returns a signed JWT.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for user: {}", request.getUsernameOrEmail());
            throw new BadCredentialsException("Invalid username or password");
        }

        String customerId = null;
        String accountNumber = null;
        BigDecimal balance = null;

        if (user.getCustomer() != null) {
            customerId = user.getCustomer().getCustomerId();
            List<Account> accounts = accountRepository.findByCustomerCustomerId(customerId);
            if (!accounts.isEmpty()) {
                Account primaryAccount = accounts.get(0);
                accountNumber = primaryAccount.getAccountNumber();
                balance = primaryAccount.getBalance();
            }
        }

        String token = jwtTokenProvider.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().name(),
                customerId
        );

        log.info("User {} successfully logged in with role {}", user.getUsername(), user.getRole());

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                customerId,
                accountNumber,
                balance,
                86400L
        );
    }

    /**
     * Registers a new customer user, creates customer master record, and provisions an initial active bank account.
     */
    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Create Customer record
        String customerId = "CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Customer customer = new Customer(
                customerId,
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                request.getPhone(),
                now
        );
        customerRepository.save(customer);

        // 2. Provision initial active Bank Account
        String accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String accountNumber = "SB-" + (10000000 + (long)(Math.random() * 89999999));
        BigDecimal initialDeposit = request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO;

        Account account = new Account(
                accountId,
                customer,
                accountNumber,
                initialDeposit,
                AccountStatus.ACTIVE,
                "INR",
                now,
                now
        );
        accountRepository.save(account);

        // 3. Create User record with BCrypt encrypted password
        String userId = "USER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        User user = new User(
                userId,
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                Role.ROLE_USER,
                customer,
                false,
                null,
                now,
                now
        );
        userRepository.save(user);

        // 4. Issue JWT
        String token = jwtTokenProvider.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().name(),
                customer.getCustomerId()
        );

        log.info("New user registered successfully: {} with account: {}", user.getUsername(), accountNumber);

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                customer.getCustomerId(),
                accountNumber,
                initialDeposit,
                86400L
        );
    }

    /**
     * Provisions a new Admin user with elevated privileges.
     */
    @Transactional
    public AuthResponse createAdmin(CreateAdminRequest request) {
        if (!adminSecretKey.equals(request.getAdminSecretKey())) {
            log.warn("Unauthorized attempt to provision admin user: {}", request.getUsername());
            throw new BadCredentialsException("Invalid admin master secret key");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Admin username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Admin email '" + request.getEmail() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
        String userId = "ADMIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        User admin = new User(
                userId,
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                Role.ROLE_ADMIN,
                null,
                false,
                null,
                now,
                now
        );
        userRepository.save(admin);

        String token = jwtTokenProvider.generateToken(
                admin.getUsername(),
                admin.getUserId(),
                admin.getRole().name(),
                null
        );

        log.info("New admin user created successfully: {}", admin.getUsername());

        return new AuthResponse(
                token,
                admin.getUserId(),
                admin.getUsername(),
                admin.getEmail(),
                admin.getRole().name(),
                null,
                null,
                null,
                86400L
        );
    }

    /**
     * Retrieves current user profile details.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        String customerId = null;
        String fullName = null;
        String accountNumber = null;
        BigDecimal balance = null;

        if (user.getCustomer() != null) {
            customerId = user.getCustomer().getCustomerId();
            fullName = user.getCustomer().getFirstName() + " " + user.getCustomer().getLastName();
            List<Account> accounts = accountRepository.findByCustomerCustomerId(customerId);
            if (!accounts.isEmpty()) {
                Account primaryAccount = accounts.get(0);
                accountNumber = primaryAccount.getAccountNumber();
                balance = primaryAccount.getBalance();
            }
        } else {
            fullName = "System Administrator";
        }

        return new UserProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                customerId,
                fullName,
                accountNumber,
                balance,
                user.isTwoFactorEnabled(),
                user.getCreatedAt()
        );
    }
}
