package com.yanjiazheng.dslock.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author hp
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        System.out.println("Parameter error: " + ex.getMessage());
        return ResponseEntity.status(400).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRateLimit(RuntimeException ex) {
        System.out.println("Rate limit error: " + ex.getMessage());
        return ResponseEntity.status(429).body("Too Many Requests: " + ex.getMessage());
    }
}
