package com.agent.financial_advisor.model;

import com.agent.financial_advisor.config.VectorType;
import jakarta.persistence.*;
import lombok.Data;
import com.pgvector.PGvector;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "hubspot_documents", indexes = {
        @Index(name = "idx_hubspot_user", columnList = "user_id"),
        @Index(name = "idx_hubspot_contact_id", columnList = "hubspot_contact_id")
})
@Data
public class HubspotDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String hubspotContactId;
    private String firstName;
    private String lastName;
    private String email;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String allProperties;

    @Column(columnDefinition = "vector(1536)")
    @Type(VectorType.class)
    private PGvector embedding;

    private LocalDateTime lastModified;
    private LocalDateTime indexedAt;

    @PrePersist
    protected void onCreate() {
        indexedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getHubspotContactId() {
        return hubspotContactId;
    }

    public void setHubspotContactId(String hubspotContactId) {
        this.hubspotContactId = hubspotContactId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getAllProperties() {
        return allProperties;
    }

    public void setAllProperties(String allProperties) {
        this.allProperties = allProperties;
    }

    public PGvector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(PGvector embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }
}
