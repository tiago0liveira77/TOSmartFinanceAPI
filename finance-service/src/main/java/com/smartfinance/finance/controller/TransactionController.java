package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.request.CreateTransactionRequest;
import com.smartfinance.finance.dto.request.CsvConfirmRequest;
import com.smartfinance.finance.dto.request.UpdateTransactionRequest;
import com.smartfinance.finance.dto.response.CsvImportResponse;
import com.smartfinance.finance.dto.response.CsvPreviewResponse;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final CsvImportService csvImportService;

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> findAll(
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "categoryIds", required = false) List<UUID> categoryIds,
            @RequestParam(name = "types", required = false) List<TransactionType> types,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "settled", required = false) Boolean settled,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "date,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return ApiResponse.ok(transactionService.findAll(accountId, categoryIds, types,
                startDate, endDate, minAmount, maxAmount, search, settled, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.created(transactionService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> findById(@PathVariable("id") UUID id) {
        return ApiResponse.ok(transactionService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@PathVariable("id") UUID id,
                                                   @Valid @RequestBody UpdateTransactionRequest request) {
        return ApiResponse.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        transactionService.delete(id);
    }

    @DeleteMapping("/recurrence-group/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable("groupId") UUID groupId) {
        transactionService.deleteGroup(groupId);
    }

    @PostMapping("/import/confirm")
    public ApiResponse<CsvImportResponse> confirmImport(@Valid @RequestBody CsvConfirmRequest request) {
        return ApiResponse.ok(csvImportService.confirmImport(request));
    }

    @PostMapping("/import/preview")
    public ApiResponse<CsvPreviewResponse> previewCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "accountId") UUID accountId) {
        return ApiResponse.ok(csvImportService.previewCsv(file, accountId));
    }

    @PostMapping("/import")
    public ApiResponse<CsvImportResponse> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "accountId") UUID accountId) {
        return ApiResponse.ok(csvImportService.importCsv(file, accountId));
    }
}