package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.request.CreateAccountRequest;
import com.smartfinance.finance.dto.request.UpdateAccountRequest;
import com.smartfinance.finance.dto.response.AccountResponse;
import com.smartfinance.finance.dto.response.AccountSummaryResponse;
import com.smartfinance.finance.service.AccountService;
import com.smartfinance.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<List<AccountResponse>> findAll() {
        return ApiResponse.ok(accountService.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.created(accountService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> findById(@PathVariable("id") UUID id) {
        return ApiResponse.ok(accountService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountResponse> update(@PathVariable("id") UUID id,
                                               @Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.ok(accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        accountService.delete(id);
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<AccountSummaryResponse> getSummary(@PathVariable("id") UUID id) {
        return ApiResponse.ok(accountService.getSummary(id));
    }
}