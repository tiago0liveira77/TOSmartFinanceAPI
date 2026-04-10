package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Representa uma linha do CSV após parsing, sem gravar em DB.
 * Usada no endpoint /import/preview para o utilizador confirmar antes de importar.
 *
 * @param lineNumber número da linha no CSV (começa em 2, linha 1 é o header)
 * @param status     "VALID" se todos os campos parsed corretamente, "INVALID" se houver erros
 * @param date       data da transação (YYYY-MM-DD) ou null se o parse falhou
 * @param description descrição da transação (sempre presente, é texto livre)
 * @param amount     valor absoluto ou null se o parse falhou
 * @param type       INCOME, EXPENSE ou TRANSFER — null se inválido
 * @param errors     lista de mensagens de erro desta linha (vazia se VALID)
 */
public record CsvPreviewRow(
        int lineNumber,
        String status,
        String date,
        String description,
        BigDecimal amount,
        String type,
        List<String> errors
) {}
