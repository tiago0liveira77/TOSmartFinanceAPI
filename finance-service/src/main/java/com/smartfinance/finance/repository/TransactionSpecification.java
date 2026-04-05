package com.smartfinance.finance.repository;

import com.smartfinance.finance.entity.Transaction;
import com.smartfinance.finance.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TransactionSpecification {

    private TransactionSpecification() {}

    public static Specification<Transaction> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Transaction> forUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Transaction> forAccount(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Transaction> forCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> forType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> dateFrom(LocalDate date) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), date);
    }

    public static Specification<Transaction> dateTo(LocalDate date) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), date);
    }

    public static Specification<Transaction> minAmount(BigDecimal amount) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), amount);
    }

    public static Specification<Transaction> maxAmount(BigDecimal amount) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), amount);
    }

    public static Specification<Transaction> descriptionContains(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("description")), "%" + search.toLowerCase() + "%");
    }

    public static Specification<Transaction> build(UUID userId, UUID accountId, UUID categoryId,
                                                   TransactionType type, LocalDate startDate,
                                                   LocalDate endDate, BigDecimal minAmount,
                                                   BigDecimal maxAmount, String search) {
        Specification<Transaction> spec = Specification.where(forUser(userId)).and(notDeleted());

        if (accountId != null)  spec = spec.and(forAccount(accountId));
        if (categoryId != null) spec = spec.and(forCategory(categoryId));
        if (type != null)       spec = spec.and(forType(type));
        if (startDate != null)  spec = spec.and(dateFrom(startDate));
        if (endDate != null)    spec = spec.and(dateTo(endDate));
        if (minAmount != null)  spec = spec.and(minAmount(minAmount));
        if (maxAmount != null)  spec = spec.and(maxAmount(maxAmount));
        if (search != null && !search.isBlank()) spec = spec.and(descriptionContains(search));

        return spec;
    }
}