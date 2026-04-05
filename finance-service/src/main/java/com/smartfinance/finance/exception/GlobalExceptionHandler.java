package com.smartfinance.finance.exception;

import com.smartfinance.shared.dto.ErrorResponse;
import com.smartfinance.shared.exception.SmartFinanceException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SmartFinanceException.class)
    public ResponseEntity<ErrorResponse> handleSmartFinanceException(SmartFinanceException ex) {
        ErrorResponse body = ErrorResponse.of(ex.getMessage(), ex.getStatus().value());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        ErrorResponse body = ErrorResponse.of("Validation failed", 400, errors);
        return ResponseEntity.badRequest().body(body);
    }
}
