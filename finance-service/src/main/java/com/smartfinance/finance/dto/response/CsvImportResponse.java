package com.smartfinance.finance.dto.response;

import java.util.List;
import java.util.UUID;

public record CsvImportResponse(
        UUID importId,
        int total,
        int imported,
        int failed,
        List<String> errors
) {}