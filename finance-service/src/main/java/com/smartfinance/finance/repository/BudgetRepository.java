package com.smartfinance.finance.repository;

import com.smartfinance.finance.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserId(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}