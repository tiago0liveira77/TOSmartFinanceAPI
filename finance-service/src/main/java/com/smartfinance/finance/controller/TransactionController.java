package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.request.CreateTransactionRequest;
import com.smartfinance.finance.dto.request.UpdateTransactionRequest;
import com.smartfinance.finance.dto.response.CsvImportResponse;
import com.smartfinance.finance.dto.response.TransactionResponse;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.service.CsvImportService;
import com.smartfinance.finance.service.TransactionService;
import com.smartfinance.shared.dto.ApiResponse;
import com.smartfinance.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final CsvImportService csvImportService;

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> findAll(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "date,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return ApiResponse.ok(transactionService.findAll(accountId, categoryId, type,
                startDate, endDate, minAmount, maxAmount, search, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.created(transactionService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> findById(@PathVariable UUID id) {
        return ApiResponse.ok(transactionService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateTransactionRequest request) {
        return ApiResponse.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        transactionService.delete(id);
    }

    @PostMapping("/import")
    public ApiResponse<CsvImportResponse> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accountId") UUID accountId) {
        return ApiResponse.ok(csvImportService.importCsv(file, accountId));
    }
}