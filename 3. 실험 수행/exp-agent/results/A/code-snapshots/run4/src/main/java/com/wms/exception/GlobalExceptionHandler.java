package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WmsException.class)
    public ResponseEntity<ApiResponse<Object>> handleWmsException(WmsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(error)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "An unexpected error occurred");
        error.put("detail", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(error)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
