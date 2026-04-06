package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.request.CreateAccountRequest;
import com.smartfinance.finance.dto.request.UpdateAccountRequest;
import com.smartfinance.finance.dto.response.AccountResponse;
import com.smartfinance.finance.dto.response.AccountSummaryResponse;
import com.smartfinance.finance.entity.Account;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.exception.AccountNotFoundException;
import com.smartfinance.finance.repository.AccountRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public List<AccountResponse> findAll() {
        UUID userId = getUserId();
        return accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream().map(AccountResponse::from).toList();
    }

    public AccountResponse findById(UUID id) {
        UUID userId = getUserId();
        return accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        UUID userId = getUserId();
        Account account = new Account();
        account.setUserId(userId);
        account.setName(request.name());
        account.setType(request.type());
        account.setCurrency(request.currency());
        account.setColor(request.color());
        account.setIcon(request.icon());
        if (request.initialBalance() != null) {
            account.setBalance(request.initialBalance());
        }
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, UpdateAccountRequest request) {
        UUID userId = getUserId();
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setName(request.name());
        if (request.color() != null) account.setColor(request.color());
        if (request.icon() != null) account.setIcon(request.icon());
        if (request.isActive() != null) account.setActive(request.isActive());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getUserId();
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setDeletedAt(java.time.LocalDateTime.now());
        accountRepository.save(account);
    }

    public AccountSummaryResponse getSummary(UUID id) {
        UUID userId = getUserId();
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));

        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);

        BigDecimal income = transactionRepository.sumByAccountAndTypeAndPeriod(
                userId, id, TransactionType.INCOME, start, end);
        BigDecimal expenses = transactionRepository.sumByAccountAndTypeAndPeriod(
                userId, id, TransactionType.EXPENSE, start, end);

        return new AccountSummaryResponse(id, account.getBalance(), income, expenses,
                income.subtract(expenses));
    }

    @Transactional
    public void adjustBalance(UUID accountId, BigDecimal delta) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);
    }
}