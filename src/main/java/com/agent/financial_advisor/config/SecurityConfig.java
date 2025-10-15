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
                        // Public endpoints
                        .requestMatchers("/login", "/css/**", "/default-ui.css", "/js/**", "/images/**").permitAll()

                        // HubSpot callback needs special handling - must be accessible but will check auth inside
                        .requestMatchers("/hubspot/callback").permitAll()

                        // Logout endpoint
                        .requestMatchers("/logout").permitAll()

                        // API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Chat page and websocket require authentication
                        .requestMatchers("/chat-page", "/ws/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/chat-page", true)
                        .loginPage("/login")
                        .failureUrl("/login?error=true")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }
}