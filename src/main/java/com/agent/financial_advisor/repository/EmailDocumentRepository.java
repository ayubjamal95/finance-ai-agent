package com.agent.financial_advisor.repository;

import com.agent.financial_advisor.model.EmailDocument;
import com.agent.financial_advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmailDocumentRepository extends JpaRepository<EmailDocument, Long> {
    @Query(value = "SELECT * FROM email_documents WHERE user_id = :userId " +
            "ORDER BY embedding <-> CAST(:embedding AS vector) LIMIT :limit",
            nativeQuery = true)
    List<EmailDocument> findSimilar(@Param("userId") Long userId,
                                    @Param("embedding") String embedding,
                                    @Param("limit") int limit);

    List<EmailDocument> findByUserOrderByEmailDateDesc(User user);
    boolean existsByGmailMessageId(String gmailMessageId);
}
