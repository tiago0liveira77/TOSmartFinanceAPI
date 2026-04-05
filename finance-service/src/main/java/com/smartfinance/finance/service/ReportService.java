package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.response.*;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public ReportSummaryResponse getSummary(int year, int month) {
        UUID userId = getUserId();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        BigDecimal totalIncome = transactionRepository
                .sumByUserIdAndTypeAndPeriod(userId, TransactionType.INCOME, start, end);
        BigDecimal totalExpenses = transactionRepository
                .sumByUserIdAndTypeAndPeriod(userId, TransactionType.EXPENSE, start, end);
        BigDecimal balance = totalIncome.subtract(totalExpenses);

        BigDecimal savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
                ? balance.divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Object[]> categoryData = transactionRepository
                .findCategoryBreakdown(userId, TransactionType.EXPENSE, start, end);

        List<CategoryReportResponse> topCategories = categoryData.stream()
                .limit(5)
                .map(row -> {
                    BigDecimal amount = (BigDecimal) row[2];
                    BigDecimal pct = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                            ? amount.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new CategoryReportResponse(
                            (UUID) row[0], (String) row[1], amount, pct,
                            ((Long) row[3]).intValue());
                }).toList();

        return new ReportSummaryResponse(totalIncome, totalExpenses, balance, savingsRate, topCategories);
    }

    public List<CategoryReportResponse> getByCategory(int year, int month, TransactionType type) {
        UUID userId = getUserId();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        List<Object[]> data = transactionRepository.findCategoryBreakdown(userId, type, start, end);

        BigDecimal total = data.stream()
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return data.stream().map(row -> {
            BigDecimal amount = (BigDecimal) row[2];
            BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new CategoryReportResponse(
                    (UUID) row[0], (String) row[1], amount, pct, ((Long) row[3]).intValue());
        }).toList();
    }

    public List<MonthlyTrendResponse> getMonthlyTrend(int year, int months) {
        UUID userId = getUserId();
        List<MonthlyTrendResponse> result = new ArrayList<>();
        LocalDate cursor = LocalDate.of(year, 1, 1).minusMonths(months - 1);

        for (int i = 0; i < months; i++) {
            LocalDate start = cursor.withDayOfMonth(1);
            LocalDate end = start.plusMonths(1);

            BigDecimal income = transactionRepository
                    .sumByUserIdAndTypeAndPeriod(userId, TransactionType.INCOME, start, end);
            BigDecimal expenses = transactionRepository
                    .sumByUserIdAndTypeAndPeriod(userId, TransactionType.EXPENSE, start, end);

            result.add(new MonthlyTrendResponse(
                    start.format(MONTH_FMT), income, expenses, income.subtract(expenses)));
            cursor = cursor.plusMonths(1);
        }

        return result;
    }

    public List<BudgetStatusResponse> getBudgetStatus() {
        return budgetService.getBudgetStatus();
    }
}