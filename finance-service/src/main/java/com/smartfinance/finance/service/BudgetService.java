package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.request.CreateBudgetRequest;
import com.smartfinance.finance.dto.request.UpdateBudgetRequest;
import com.smartfinance.finance.dto.response.BudgetResponse;
import com.smartfinance.finance.dto.response.BudgetStatusResponse;
import com.smartfinance.finance.entity.Budget;
import com.smartfinance.finance.entity.BudgetPeriod;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.exception.BudgetNotFoundException;
import com.smartfinance.finance.exception.CategoryNotFoundException;
import com.smartfinance.finance.repository.BudgetRepository;
import com.smartfinance.finance.repository.CategoryRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import com.smartfinance.shared.event.BudgetThresholdEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisherService eventPublisher;

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public List<BudgetResponse> findAll() {
        return budgetRepository.findByUserId(getUserId())
                .stream().map(BudgetResponse::from).toList();
    }

    public BudgetResponse findById(UUID id) {
        UUID userId = getUserId();
        return budgetRepository.findByIdAndUserId(id, userId)
                .map(BudgetResponse::from)
                .orElseThrow(() -> new BudgetNotFoundException(id));
    }

    @Transactional
    public BudgetResponse create(CreateBudgetRequest request) {
        UUID userId = getUserId();

        categoryRepository.findById(request.categoryId())
                .filter(c -> c.isSystem() || userId.equals(c.getUserId()))
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategory(categoryRepository.findById(request.categoryId()).get());
        budget.setAmount(request.amount());
        budget.setPeriod(request.period());
        budget.setStartDate(request.startDate());
        budget.setEndDate(request.endDate());
        if (request.alertThreshold() != null) budget.setAlertThreshold(request.alertThreshold());

        BudgetResponse saved = BudgetResponse.from(budgetRepository.save(budget));
        log.info("[FINANCE] Budget created: userId={}, budgetId={}, category={}, amount={}, period={}",
                userId, saved.id(), saved.categoryName(), saved.amount(), saved.period());
        return saved;
    }

    @Transactional
    public BudgetResponse update(UUID id, UpdateBudgetRequest request) {
        UUID userId = getUserId();
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException(id));

        if (request.amount() != null) budget.setAmount(request.amount());
        if (request.alertThreshold() != null) budget.setAlertThreshold(request.alertThreshold());
        if (request.endDate() != null) budget.setEndDate(request.endDate());

        return BudgetResponse.from(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getUserId();
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException(id));
        budgetRepository.delete(budget);
    }

    public List<BudgetStatusResponse> getBudgetStatus() {
        UUID userId = getUserId();
        LocalDate now = LocalDate.now();

        return budgetRepository.findByUserId(userId).stream()
                .map(budget -> {
                    LocalDate start, end;
                    if (budget.getPeriod() == BudgetPeriod.MONTHLY) {
                        start = now.withDayOfMonth(1);
                        end   = start.plusMonths(1);
                    } else {
                        start = now.withDayOfYear(1);
                        end   = start.plusYears(1);
                    }

                    BigDecimal spent = transactionRepository.sumByCategoryAndTypeAndPeriod(
                            userId, budget.getCategory().getId(), TransactionType.EXPENSE, start, end);

                    BigDecimal percentage = budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                            ? spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    String status = percentage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "EXCEEDED"
                            : percentage.compareTo(BigDecimal.valueOf(budget.getAlertThreshold())) >= 0
                            ? "WARNING" : "OK";

                    BigDecimal remaining = budget.getAmount().subtract(spent).max(BigDecimal.ZERO);

                    if (!"OK".equals(status)) {
                        // Limiar ultrapassado (WARNING ou EXCEEDED) — publica evento para notification-service
                        log.info("[FINANCE][RABBIT] Budget threshold reached: budgetId={}, category={}, status={}, pct={}%",
                                budget.getId(), budget.getCategory().getName(), status, percentage.intValue());
                        eventPublisher.publishBudgetThreshold(new BudgetThresholdEvent(
                                userId, budget.getId(), percentage.intValue()));
                    }

                    return new BudgetStatusResponse(budget.getId(),
                            budget.getCategory().getName(), budget.getAmount(),
                            spent, percentage, remaining, status);
                }).toList();
    }
}