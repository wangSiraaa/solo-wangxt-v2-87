package com.example.quota.api;

import com.example.quota.service.LedgerRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LedgerRuleException.class)
    public ResponseEntity<Map<String, Object>> rule(LedgerRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "RULE_VIOLATION",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage() == null ? "请求参数有误" : ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()));
    }
}
