package com.agent.financial_advisor.repository;

import com.agent.financial_advisor.model.OngoingInstruction;
import com.agent.financial_advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OngoingInstructionRepository extends JpaRepository<OngoingInstruction, Long> {
    List<OngoingInstruction> findByUserAndActiveTrue(User user);
}
