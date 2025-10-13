package com.agent.financial_advisor.model;



import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;
    @Column(length = 2000) //
    private String googleAccessToken;
    @Column(length = 2000) //
    private String googleRefreshToken;
    private LocalDateTime googleTokenExpiry;

    @Column(length = 2000) //
    private String hubspotAccessToken;
    @Column(length = 2000) //
    private String hubspotRefreshToken;
    private LocalDateTime hubspotTokenExpiry;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGoogleAccessToken() {
        return googleAccessToken;
    }

    public void setGoogleAccessToken(String googleAccessToken) {
        this.googleAccessToken = googleAccessToken;
    }

    public String getGoogleRefreshToken() {
        return googleRefreshToken;
    }

    public void setGoogleRefreshToken(String googleRefreshToken) {
        this.googleRefreshToken = googleRefreshToken;
    }

    public LocalDateTime getGoogleTokenExpiry() {
        return googleTokenExpiry;
    }

    public void setGoogleTokenExpiry(LocalDateTime googleTokenExpiry) {
        this.googleTokenExpiry = googleTokenExpiry;
    }

    public String getHubspotAccessToken() {
        return hubspotAccessToken;
    }

    public void setHubspotAccessToken(String hubspotAccessToken) {
        this.hubspotAccessToken = hubspotAccessToken;
    }

    public String getHubspotRefreshToken() {
        return hubspotRefreshToken;
    }

    public void setHubspotRefreshToken(String hubspotRefreshToken) {
        this.hubspotRefreshToken = hubspotRefreshToken;
    }

    public LocalDateTime getHubspotTokenExpiry() {
        return hubspotTokenExpiry;
    }

    public void setHubspotTokenExpiry(LocalDateTime hubspotTokenExpiry) {
        this.hubspotTokenExpiry = hubspotTokenExpiry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
