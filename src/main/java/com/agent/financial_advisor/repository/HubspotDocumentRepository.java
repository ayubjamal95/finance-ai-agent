package com.agent.financial_advisor.repository;

import com.agent.financial_advisor.model.HubspotDocument;
import com.agent.financial_advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HubspotDocumentRepository extends JpaRepository<HubspotDocument, Long> {

    Optional<HubspotDocument> findByUserAndHubspotContactId(User user, String hubspotContactId);

    @Query(value = "SELECT * FROM hubspot_documents WHERE user_id = :userId " +
            "ORDER BY embedding <-> CAST(:embedding AS vector) LIMIT :limit",
            nativeQuery = true)
    List<HubspotDocument> findSimilar(@Param("userId") Long userId,
                                      @Param("embedding") String embedding,
                                      @Param("limit") int limit);

    List<HubspotDocument> findByUserOrderByLastModifiedDesc(User user);
}
