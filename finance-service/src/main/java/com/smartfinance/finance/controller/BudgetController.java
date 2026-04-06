package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.request.CreateBudgetRequest;
import com.smartfinance.finance.dto.request.UpdateBudgetRequest;
import com.smartfinance.finance.dto.response.BudgetResponse;
import com.smartfinance.finance.service.BudgetService;
import com.smartfinance.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ApiResponse<List<BudgetResponse>> findAll() {
        return ApiResponse.ok(budgetService.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        return ApiResponse.created(budgetService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BudgetResponse> findById(@PathVariable("id") UUID id) {
        return ApiResponse.ok(budgetService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetResponse> update(@PathVariable("id") UUID id,
                                              @Valid @RequestBody UpdateBudgetRequest request) {
        return ApiResponse.ok(budgetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        budgetService.delete(id);
    }
}