package com.agent.financial_advisor.repository;

import com.agent.financial_advisor.model.Message;
import com.agent.financial_advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByUserOrderByCreatedAtAsc(User user);
    List<Message> findTop20ByUserOrderByCreatedAtDesc(User user);
}