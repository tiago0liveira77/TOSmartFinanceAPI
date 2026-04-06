package com.smartfinance.finance.repository;

import com.smartfinance.finance.entity.Transaction;
import com.smartfinance.finance.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Transaction> findByRecurrenceGroupIdAndUserIdAndDeletedAtIsNull(UUID recurrenceGroupId, UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.settled = false AND t.date <= :today AND t.deletedAt IS NULL")
    List<Transaction> findUnsettledDue(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.userId = :userId AND t.type = :type " +
            "AND t.date >= :start AND t.date < :end AND t.deletedAt IS NULL")
    BigDecimal sumByUserIdAndTypeAndPeriod(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.userId = :userId AND t.account.id = :accountId AND t.type = :type " +
            "AND t.date >= :start AND t.date < :end AND t.deletedAt IS NULL")
    BigDecimal sumByAccountAndTypeAndPeriod(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT t.category.id, t.category.name, SUM(t.amount), COUNT(t) " +
            "FROM Transaction t " +
            "WHERE t.userId = :userId AND t.type = :type " +
            "AND t.date >= :start AND t.date < :end " +
            "AND t.deletedAt IS NULL AND t.category IS NOT NULL " +
            "GROUP BY t.category.id, t.category.name " +
            "ORDER BY SUM(t.amount) DESC")
    List<Object[]> findCategoryBreakdown(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.userId = :userId AND t.category.id = :categoryId AND t.type = :type " +
            "AND t.date >= :start AND t.date < :end AND t.deletedAt IS NULL")
    BigDecimal sumByCategoryAndTypeAndPeriod(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}