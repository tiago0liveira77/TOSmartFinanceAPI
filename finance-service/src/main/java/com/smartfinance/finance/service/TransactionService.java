package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.request.BatchCreateTransactionRequest;
import com.smartfinance.finance.dto.request.BatchCreateTransactionRow;
import com.smartfinance.finance.dto.request.CreateTransactionRequest;
import com.smartfinance.finance.dto.request.UpdateTransactionRequest;
import com.smartfinance.finance.dto.response.BatchCreateTransactionResponse;
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
import com.smartfinance.shared.event.TransactionImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final CategorizationHintService hintService;
    private final EventPublisherService eventPublisher;

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public PageResponse<TransactionResponse> findAll(UUID accountId, List<UUID> categoryIds,
                                                     List<TransactionType> types, LocalDate startDate,
                                                     LocalDate endDate, BigDecimal minAmount,
                                                     BigDecimal maxAmount, String search,
                                                     Boolean settled, Pageable pageable) {
        UUID userId = getUserId();
        Specification<Transaction> spec = TransactionSpecification.build(
                userId, accountId, categoryIds, types, startDate, endDate, minAmount, maxAmount, search, settled);

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

        boolean recurring = request.isRecurring()
                && request.recurrenceRule() != null
                && request.occurrences() != null
                && request.occurrences() > 1;

        UUID groupId = recurring ? UUID.randomUUID() : null;
        int count = recurring ? request.occurrences() : 1;

        BigDecimal amount = request.amount().abs();
        LocalDate today = LocalDate.now();

        List<Transaction> instances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate date = nextDate(request.date(), request.recurrenceRule(), i);
            boolean settled = !date.isAfter(today);

            Transaction t = new Transaction();
            t.setUserId(userId);
            t.setAccount(account);
            t.setCategory(category);
            t.setAmount(amount);
            t.setType(request.type());
            t.setDescription(request.description());
            t.setNotes(request.notes());
            t.setDate(date);
            t.setRecurring(recurring);
            t.setRecurrenceRule(recurring ? request.recurrenceRule() : null);
            t.setRecurrenceGroupId(groupId);
            t.setSettled(settled);
            instances.add(t);
        }

        List<Transaction> saved = transactionRepository.saveAll(instances);

        // Ajusta o saldo apenas pelas instâncias settled (data <= hoje)
        long settledCount = instances.stream().filter(Transaction::isSettled).count();
        if (settledCount > 0) {
            BigDecimal delta = request.type() == TransactionType.INCOME ? amount : amount.negate();
            accountService.adjustBalance(account.getId(), delta.multiply(BigDecimal.valueOf(settledCount)));
        }

        eventPublisher.publishTransactionCreated(new TransactionCreatedEvent(
                userId, saved.get(0).getId(), amount, request.description()));

        log.info("[FINANCE] Transaction(s) created: userId={}, firstTxId={}, amount={}, type={}, count={}",
                userId, saved.get(0).getId(), amount, request.type(), saved.size());
        return TransactionResponse.from(saved.get(0));
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
            transaction.setAiCategorized(false);
            // Guardar hint: o utilizador corrigiu/confirmou esta categoria para esta descrição
            hintService.saveOrUpdate(userId, transaction.getDescription(), category);
        } else {
            transaction.setCategory(null);
            transaction.setAiCategorized(false);
        }

        if (request.description() != null) transaction.setDescription(request.description());
        if (request.notes() != null) transaction.setNotes(request.notes());

        LocalDate today = LocalDate.now();
        boolean wasSettled = transaction.isSettled();
        LocalDate newDate = request.date() != null ? request.date() : transaction.getDate();
        BigDecimal newAmount = request.amount() != null ? request.amount() : transaction.getAmount();
        boolean willBeSettled = !newDate.isAfter(today);

        BigDecimal oldAmount = transaction.getAmount();
        UUID accountId = transaction.getAccount().getId();
        TransactionType type = transaction.getType();

        if (!wasSettled && willBeSettled) {
            // Agendada → realizada: aplicar o valor ao saldo pela primeira vez
            BigDecimal delta = type == TransactionType.INCOME ? newAmount : newAmount.negate();
            accountService.adjustBalance(accountId, delta);
            transaction.setSettled(true);
        } else if (wasSettled && !willBeSettled) {
            // Realizada → agendada: reverter o valor do saldo
            BigDecimal delta = type == TransactionType.INCOME ? oldAmount.negate() : oldAmount;
            accountService.adjustBalance(accountId, delta);
            transaction.setSettled(false);
        } else if (wasSettled && newAmount.compareTo(oldAmount) != 0) {
            // Realizada → realizada com valor diferente: ajustar pela diferença
            BigDecimal diff = newAmount.subtract(oldAmount);
            BigDecimal delta = type == TransactionType.INCOME ? diff : diff.negate();
            accountService.adjustBalance(accountId, delta);
        }
        // Agendada → agendada: nada a fazer no saldo

        if (request.date() != null) transaction.setDate(newDate);
        if (request.amount() != null) transaction.setAmount(newAmount);

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public BatchCreateTransactionResponse createBatch(BatchCreateTransactionRequest request) {
        UUID userId = getUserId();
        UUID batchId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        int created = 0;
        int failed = 0;
        List<UUID> needsAiIds = new ArrayList<>();

        for (BatchCreateTransactionRow row : request.transactions()) {
            try {
                Account account = accountRepository
                        .findByIdAndUserIdAndDeletedAtIsNull(row.accountId(), userId)
                        .orElseThrow(() -> new AccountNotFoundException(row.accountId()));

                Category category = null;
                if (row.categoryId() != null) {
                    category = categoryRepository.findById(row.categoryId())
                            .filter(c -> c.isSystem() || userId.equals(c.getUserId()))
                            .orElse(null);
                    if (category != null) {
                        hintService.saveOrUpdate(userId, row.description(), category);
                    }
                }

                BigDecimal amount = row.amount().abs();
                boolean settled = !row.date().isAfter(today);

                Transaction t = new Transaction();
                t.setUserId(userId);
                t.setAccount(account);
                t.setCategory(category);
                t.setAmount(amount);
                t.setType(row.type());
                t.setDescription(row.description());
                t.setDate(row.date());
                t.setImportId(batchId);
                t.setSettled(settled);
                t.setAiCategorized(false);

                Transaction saved = transactionRepository.save(t);
                created++;

                if (settled) {
                    BigDecimal delta = row.type() == TransactionType.INCOME ? amount : amount.negate();
                    accountService.adjustBalance(account.getId(), delta);
                }

                if (category == null) {
                    needsAiIds.add(saved.getId());
                }

            } catch (Exception e) {
                log.warn("[FINANCE] Batch create failed for row: {}", e.getMessage());
                failed++;
            }
        }

        if (!needsAiIds.isEmpty()) {
            eventPublisher.publishTransactionImported(
                    new TransactionImportedEvent(userId, batchId, needsAiIds));
        }

        log.info("[FINANCE] Batch create complete: userId={}, created={}, failed={}, ai={}",
                userId, created, failed, needsAiIds.size());
        return new BatchCreateTransactionResponse(request.transactions().size(), created, failed);
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getUserId();
        Transaction transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (transaction.isSettled()) {
            BigDecimal delta = transaction.getType() == TransactionType.INCOME
                    ? transaction.getAmount().negate() : transaction.getAmount();
            accountService.adjustBalance(transaction.getAccount().getId(), delta);
        }

        transaction.setDeletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        log.info("[FINANCE] Transaction deleted (soft): txId={}, userId={}", id, userId);
    }

    @Transactional
    public void deleteGroup(UUID recurrenceGroupId) {
        UUID userId = getUserId();
        List<Transaction> group = transactionRepository
                .findByRecurrenceGroupIdAndUserIdAndDeletedAtIsNull(recurrenceGroupId, userId);

        LocalDateTime now = LocalDateTime.now();
        for (Transaction t : group) {
            if (t.isSettled()) {
                BigDecimal delta = t.getType() == TransactionType.INCOME
                        ? t.getAmount().negate() : t.getAmount();
                accountService.adjustBalance(t.getAccount().getId(), delta);
            }
            t.setDeletedAt(now);
        }
        transactionRepository.saveAll(group);
    }

    private LocalDate nextDate(LocalDate base, RecurrenceRule rule, int index) {
        if (index == 0 || rule == null) return base;
        return switch (rule) {
            case DAILY     -> base.plusDays(index);
            case WEEKLY    -> base.plusWeeks(index);
            case MONTHLY   -> base.plusMonths(index);
            case BIMONTHLY -> base.plusMonths((long) index * 2);
            case YEARLY    -> base.plusYears(index);
        };
    }
}
