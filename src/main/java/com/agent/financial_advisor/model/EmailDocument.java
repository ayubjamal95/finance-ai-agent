package com.agent.financial_advisor.model;

import com.agent.financial_advisor.config.VectorType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.pgvector.PGvector;
import org.hibernate.annotations.Type;


@Entity
@Table(name = "email_documents", indexes = {
        @Index(name = "idx_email_user", columnList = "user_id"),
        @Index(name = "idx_email_gmail_id", columnList = "gmail_message_id")
})
@Data
public class EmailDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true)
    private String gmailMessageId;

    private String fromEmail;
    private String fromName;
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "vector(1536)")
    @Type(VectorType.class)
    private PGvector embedding;

    private LocalDateTime emailDate;
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

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public PGvector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(PGvector embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getEmailDate() {
        return emailDate;
    }

    public void setEmailDate(LocalDateTime emailDate) {
        this.emailDate = emailDate;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }
}
