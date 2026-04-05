package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.request.CreateTransactionRequest;
import com.smartfinance.finance.dto.request.UpdateTransactionRequest;
import com.smartfinance.finance.dto.response.TransactionResponse;
import com.smartfinance.finance.entity.*;
import com.smartfinance.finance.exception.AccountNotFoundException;
import com.smartfinance.finance.exception.CategoryNotFoundException;
import com.smartfinance.finance.exception.TransactionNotFoundException;
import com.smartfinance.finance.repository.AccountRepository;
import com.smartfinance.finance.repository.CategoryRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import com.smartfinance.finance.repository.TransactionSpecification;
import com.smartfinance.shared.dto.PageResponse;
import com.smartfinance.shared.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final EventPublisherService eventPublisher;

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public PageResponse<TransactionResponse> findAll(UUID accountId, UUID categoryId,
                                                     TransactionType type, LocalDate startDate,
                                                     LocalDate endDate, BigDecimal minAmount,
                                                     BigDecimal maxAmount, String search,
                                                     Pageable pageable) {
        UUID userId = getUserId();
        Specification<Transaction> spec = TransactionSpecification.build(
                userId, accountId, categoryId, type, startDate, endDate, minAmount, maxAmount, search);

        Page<TransactionResponse> page = transactionRepository.findAll(spec, pageable)
                .map(TransactionResponse::from);

        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public TransactionResponse findById(UUID id) {
        UUID userId = getUserId();
        return transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        UUID userId = getUserId();

        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(request.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .filter(c -> c.isSystem() || userId.equals(c.getUserId()))
                    .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
        }

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setAmount(request.amount().abs());
        transaction.setType(request.type());
        transaction.setDescription(request.description());
        transaction.setNotes(request.notes());
        transaction.setDate(request.date());
        transaction.setRecurring(request.isRecurring());
        transaction.setRecurrenceRule(request.recurrenceRule());

        Transaction saved = transactionRepository.save(transaction);

        BigDecimal delta = request.type() == TransactionType.INCOME
                ? request.amount() : request.amount().negate();
        accountService.adjustBalance(account.getId(), delta);

        eventPublisher.publishTransactionCreated(new TransactionCreatedEvent(
                userId, saved.getId(), saved.getAmount(), saved.getDescription()));

        return TransactionResponse.from(saved);
    }

    @Transactional
    public TransactionResponse update(UUID id, UpdateTransactionRequest request) {
        UUID userId = getUserId();
        Transaction transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .filter(c -> c.isSystem() || userId.equals(c.getUserId()))
                    .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
            transaction.setCategory(category);
        } else {
            transaction.setCategory(null);
        }

        if (request.description() != null) transaction.setDescription(request.description());
        if (request.notes() != null) transaction.setNotes(request.notes());
        if (request.date() != null) transaction.setDate(request.date());

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getUserId();
        Transaction transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        BigDecimal delta = transaction.getType() == TransactionType.INCOME
                ? transaction.getAmount().negate() : transaction.getAmount();
        accountService.adjustBalance(transaction.getAccount().getId(), delta);

        transaction.setDeletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }
}