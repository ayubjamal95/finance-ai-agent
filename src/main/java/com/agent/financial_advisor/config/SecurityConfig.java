package com.agent.financial_advisor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http

                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth

                        // ⭐ CRITICAL FIX: Explicitly permit this specific URI FIRST ⭐
                        // This guarantees the request bypasses Spring Security's internal OAuth filter.
                        .requestMatchers("/hubspot/callback").permitAll()

                        // Your other public/private paths (can be adjusted later)
                        .requestMatchers("/login", "/css/**", "/default-ui.css").permitAll()

                        // Apply the default rule to everything else
                        // Note: Change this to .anyRequest().authenticated() once you remove the temporary bypass
                        .anyRequest().permitAll() // Retained your temporary setting
                )
                .oauth2Login(oauth2 -> oauth2
                        // This remains for Google OAuth flow
                        .defaultSuccessUrl("/chat-page", true)
                        .loginPage("/login")
                );

        return http.build();
    }
}
