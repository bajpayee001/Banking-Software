package com.bank.transaction.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .authorizeHttpRequests(auth -> auth
                // Public auth and registration endpoints
                .requestMatchers("/api/auth/**").permitAll()
                // Public web client assets
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                // In-memory H2 database console for development
                .requestMatchers("/h2-console/**").permitAll()
                // Admin-only endpoints (system health, benchmark triggers, concurrency simulator)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/benchmark/**").hasRole("ADMIN")
                .requestMatchers("/api/concurrency/**").hasRole("ADMIN")
                // User & Admin secured business API endpoints
                .requestMatchers("/api/transactions/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/accounts/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/reports/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/ai/**").hasAnyRole("USER", "ADMIN")
                // All other business API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
