package com.smartfinance.finance.dto.response;

import java.util.List;

/**
 * Resposta do endpoint POST /api/v1/transactions/import/preview.
 * Contém todas as linhas do CSV parseadas sem qualquer escrita em DB.
 */
public record CsvPreviewResponse(
        int total,
        int validCount,
        int invalidCount,
        List<CsvPreviewRow> rows
) {}
