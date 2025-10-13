package com.agent.financial_advisor.repository;

import com.agent.financial_advisor.model.Task;
import com.agent.financial_advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserAndStatusIn(User user, List<String> statuses);

    List<Task> findByUserOrderByCreatedAtDesc(User user);
}