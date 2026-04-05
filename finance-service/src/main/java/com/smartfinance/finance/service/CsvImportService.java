package com.smartfinance.finance.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.smartfinance.finance.dto.response.CsvImportResponse;
import com.smartfinance.finance.entity.Account;
import com.smartfinance.finance.entity.Transaction;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.exception.AccountNotFoundException;
import com.smartfinance.finance.repository.AccountRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import com.smartfinance.shared.event.TransactionImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final EventPublisherService eventPublisher;

    private static final List<String> REQUIRED_HEADERS =
            Arrays.asList("date", "description", "amount", "type");

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Transactional
    public CsvImportResponse importCsv(MultipartFile file, UUID accountId) {
        UUID userId = getUserId();
        UUID importId = UUID.randomUUID();

        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<String> errors = new ArrayList<>();
        List<UUID> importedIds = new ArrayList<>();
        int total = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                errors.add("Empty file");
                return new CsvImportResponse(importId, 0, 0, 0, errors);
            }

            List<String> headerList = Arrays.stream(headers)
                    .map(h -> h.trim().toLowerCase()).toList();

            for (String required : REQUIRED_HEADERS) {
                if (!headerList.contains(required)) {
                    errors.add("Missing required header: " + required);
                }
            }
            if (!errors.isEmpty()) {
                return new CsvImportResponse(importId, 0, 0, 0, errors);
            }

            int dateIdx   = headerList.indexOf("date");
            int descIdx   = headerList.indexOf("description");
            int amountIdx = headerList.indexOf("amount");
            int typeIdx   = headerList.indexOf("type");

            String[] row;
            int lineNumber = 1;
            while ((row = reader.readNext()) != null) {
                lineNumber++;
                total++;
                try {
                    LocalDate date        = LocalDate.parse(row[dateIdx].trim());
                    String description    = row[descIdx].trim();
                    BigDecimal amount     = new BigDecimal(row[amountIdx].trim()).abs();
                    TransactionType type  = TransactionType.valueOf(row[typeIdx].trim().toUpperCase());

                    Transaction transaction = new Transaction();
                    transaction.setUserId(userId);
                    transaction.setAccount(account);
                    transaction.setAmount(amount);
                    transaction.setType(type);
                    transaction.setDescription(description);
                    transaction.setDate(date);
                    transaction.setImportId(importId);
                    transaction.setAiCategorized(false);

                    Transaction saved = transactionRepository.save(transaction);
                    importedIds.add(saved.getId());

                    BigDecimal delta = type == TransactionType.INCOME ? amount : amount.negate();
                    account.setBalance(account.getBalance().add(delta));

                } catch (DateTimeParseException e) {
                    errors.add("Line " + lineNumber + ": invalid date format (expected YYYY-MM-DD)");
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNumber + ": invalid amount");
                } catch (IllegalArgumentException e) {
                    errors.add("Line " + lineNumber + ": invalid type (INCOME, EXPENSE or TRANSFER)");
                }
            }

            accountRepository.save(account);

        } catch (CsvException | java.io.IOException e) {
            log.error("CSV import error", e);
            errors.add("Failed to parse CSV: " + e.getMessage());
        }

        int imported = importedIds.size();
        int failed   = total - imported;

        if (!importedIds.isEmpty()) {
            eventPublisher.publishTransactionImported(
                    new TransactionImportedEvent(userId, importId, importedIds));
        }

        return new CsvImportResponse(importId, total, imported, failed, errors);
    }
}