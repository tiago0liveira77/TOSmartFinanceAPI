package com.smartfinance.finance.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.smartfinance.finance.dto.request.CsvConfirmRequest;
import com.smartfinance.finance.dto.request.CsvConfirmRow;
import com.smartfinance.finance.dto.response.CsvImportResponse;
import com.smartfinance.finance.dto.response.CsvPreviewResponse;
import com.smartfinance.finance.dto.response.CsvPreviewRow;
import com.smartfinance.finance.entity.Account;
import com.smartfinance.finance.entity.Transaction;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.exception.AccountNotFoundException;
import com.smartfinance.finance.repository.AccountRepository;
import com.smartfinance.finance.repository.CategoryRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import com.smartfinance.shared.event.TransactionImportedEvent;
import com.smartfinance.shared.util.DescriptionNormalizer;
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
    private final CategoryRepository categoryRepository;
    private final EventPublisherService eventPublisher;

    private static final List<String> REQUIRED_HEADERS =
            Arrays.asList("date", "description", "amount", "type");

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /**
     * Faz o parsing do CSV e devolve uma pré-visualização de todos os registos sem gravar em DB.
     * Permite ao utilizador confirmar os dados antes de importar.
     *
     * Não faz qualquer escrita em DB nem publica eventos RabbitMQ.
     */
    public CsvPreviewResponse previewCsv(MultipartFile file, UUID accountId) {
        UUID userId = getUserId();
        log.info("[FINANCE] CSV preview started: userId={}, accountId={}, fileName={}",
                userId, accountId, file.getOriginalFilename());

        // Valida que a conta existe e pertence ao utilizador — mesmo que no import real
        accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<CsvPreviewRow> rows = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                return new CsvPreviewResponse(0, 0, 0, rows);
            }

            List<String> headerList = Arrays.stream(headers)
                    .map(h -> h.trim().toLowerCase()).toList();

            List<String> headerErrors = new ArrayList<>();
            for (String required : REQUIRED_HEADERS) {
                if (!headerList.contains(required)) {
                    headerErrors.add("Header em falta: " + required);
                }
            }
            if (!headerErrors.isEmpty()) {
                rows.add(new CsvPreviewRow(1, "INVALID", null, null, null, null, null, headerErrors));
                return new CsvPreviewResponse(1, 0, 1, rows);
            }

            int dateIdx   = headerList.indexOf("date");
            int descIdx   = headerList.indexOf("description");
            int amountIdx = headerList.indexOf("amount");
            int typeIdx   = headerList.indexOf("type");

            String[] row;
            int lineNumber = 1;
            while ((row = reader.readNext()) != null) {
                lineNumber++;
                List<String> rowErrors = new ArrayList<>();

                // Tentar parsear cada campo individualmente para mostrar erros concretos
                String date = null;
                try {
                    date = LocalDate.parse(row[dateIdx].trim()).toString();
                } catch (DateTimeParseException e) {
                    rowErrors.add("Data inválida (esperado YYYY-MM-DD)");
                }

                String description = row[descIdx].trim();
                String normalizedDescription = DescriptionNormalizer.normalize(description);

                BigDecimal amount = null;
                try {
                    amount = new BigDecimal(row[amountIdx].trim()).abs();
                } catch (NumberFormatException e) {
                    rowErrors.add("Valor inválido: \"" + row[amountIdx].trim() + "\"");
                }

                String type = null;
                try {
                    type = TransactionType.valueOf(row[typeIdx].trim().toUpperCase()).name();
                } catch (IllegalArgumentException e) {
                    rowErrors.add("Tipo inválido: \"" + row[typeIdx].trim() + "\" (INCOME, EXPENSE ou TRANSFER)");
                }

                String status = rowErrors.isEmpty() ? "VALID" : "INVALID";
                rows.add(new CsvPreviewRow(lineNumber, status, date, description, normalizedDescription, amount, type, rowErrors));
            }

        } catch (CsvException | java.io.IOException e) {
            log.error("[FINANCE] CSV preview parse error: userId={}, fileName={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            rows.add(new CsvPreviewRow(0, "INVALID", null, null, null, null, null,
                    List.of("Erro ao ler ficheiro CSV: " + e.getMessage())));
        }

        int validCount = (int) rows.stream().filter(r -> "VALID".equals(r.status())).count();
        int invalidCount = rows.size() - validCount;

        log.info("[FINANCE] CSV preview complete: userId={}, total={}, valid={}, invalid={}",
                userId, rows.size(), validCount, invalidCount);

        return new CsvPreviewResponse(rows.size(), validCount, invalidCount, rows);
    }

    /**
     * Importa as transações já validadas e editadas pelo utilizador (fluxo preview → confirm).
     * Recebe JSON em vez do ficheiro CSV — as descrições podem ter sido alteradas pelo utilizador.
     * Equivalente ao importCsv() mas sem parsing de CSV.
     */
    @Transactional
    public CsvImportResponse confirmImport(CsvConfirmRequest request) {
        UUID userId = getUserId();
        UUID importId = UUID.randomUUID();
        log.info("[FINANCE] CSV confirm import started: userId={}, accountId={}, rows={}, importId={}",
                userId, request.accountId(), request.transactions().size(), importId);

        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(request.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        List<String> errors = new ArrayList<>();
        List<UUID> importedIds        = new ArrayList<>();  // todos os importados
        List<UUID> needsAiIds         = new ArrayList<>();  // sem categoria manual → AI categoriza

        LocalDate today = LocalDate.now();

        for (CsvConfirmRow row : request.transactions()) {
            try {
                LocalDate date        = LocalDate.parse(row.date());
                BigDecimal amount     = row.amount().abs();
                TransactionType type  = TransactionType.valueOf(row.type().trim().toUpperCase());

                // Transações com data <= hoje já aconteceram → settled = true e saldo atualizado.
                // Transações futuras ficam agendadas → settled = false, saldo não é alterado agora.
                boolean settled = !date.isAfter(today);

                Transaction transaction = new Transaction();
                transaction.setUserId(userId);
                transaction.setAccount(account);
                transaction.setAmount(amount);
                transaction.setType(type);
                transaction.setDescription(row.description().trim());
                transaction.setDate(date);
                transaction.setImportId(importId);
                transaction.setSettled(settled);

                if (row.categoryId() != null) {
                    // Categoria selecionada manualmente — atribuir diretamente, sem AI
                    categoryRepository.findById(row.categoryId()).ifPresent(category -> {
                        transaction.setCategory(category);
                        transaction.setAiCategorized(false);
                    });
                } else {
                    // Sem categoria → AI irá categorizar depois
                    transaction.setAiCategorized(false);
                }

                Transaction saved = transactionRepository.save(transaction);
                importedIds.add(saved.getId());

                // Só envia para AI as transações sem categoria manual
                if (row.categoryId() == null) {
                    needsAiIds.add(saved.getId());
                }

                if (settled) {
                    BigDecimal delta = type == TransactionType.INCOME ? amount : amount.negate();
                    account.setBalance(account.getBalance().add(delta));
                }

            } catch (DateTimeParseException e) {
                errors.add("Data inválida: " + row.date());
            } catch (IllegalArgumentException e) {
                errors.add("Tipo inválido: " + row.type());
            }
        }

        accountRepository.save(account);

        int imported = importedIds.size();
        int failed   = request.transactions().size() - imported;

        log.info("[FINANCE] CSV confirm import complete: importId={}, total={}, imported={}, failed={}, manual={}, ai={}",
                importId, request.transactions().size(), imported, failed,
                imported - needsAiIds.size(), needsAiIds.size());

        // Só publica evento para AI se houver transações sem categoria manual
        if (!needsAiIds.isEmpty()) {
            eventPublisher.publishTransactionImported(
                    new TransactionImportedEvent(userId, importId, needsAiIds));
        }

        return new CsvImportResponse(importId, request.transactions().size(), imported, failed, errors);
    }

    @Transactional
    public CsvImportResponse importCsv(MultipartFile file, UUID accountId) {
        UUID userId = getUserId();
        UUID importId = UUID.randomUUID();
        log.info("[FINANCE] CSV import started: userId={}, accountId={}, fileName={}, importId={}",
                userId, accountId, file.getOriginalFilename(), importId);

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
            log.error("[FINANCE] CSV import error: userId={}, fileName={}, error={}", userId, file.getOriginalFilename(), e.getMessage(), e);
            errors.add("Failed to parse CSV: " + e.getMessage());
        }

        int imported = importedIds.size();
        int failed   = total - imported;

        log.info("[FINANCE] CSV import complete: importId={}, total={}, imported={}, failed={}",
                importId, total, imported, failed);

        if (!importedIds.isEmpty()) {
            // Publica evento para o ai-service categorizar as transações assincronamente
            eventPublisher.publishTransactionImported(
                    new TransactionImportedEvent(userId, importId, importedIds));
        }

        return new CsvImportResponse(importId, total, imported, failed, errors);
    }
}