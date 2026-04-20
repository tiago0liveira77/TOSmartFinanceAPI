package com.smartfinance.finance.dto.response;

public record BatchCreateTransactionResponse(int total, int created, int failed) {}
