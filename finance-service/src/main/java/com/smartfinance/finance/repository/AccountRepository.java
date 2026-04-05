package com.smartfinance.finance.repository;

import com.smartfinance.finance.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Account> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}