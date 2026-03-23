package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = determineHttpStatus(ex.getCode());
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred: " + ex.getMessage()));
    }

    private HttpStatus determineHttpStatus(String code) {
        return switch (code) {
            case "NOT_FOUND", "PO_NOT_FOUND", "PRODUCT_NOT_FOUND", "LOCATION_NOT_FOUND", "RECEIPT_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "OVER_DELIVERY", "STORAGE_TYPE_MISMATCH", "LOCATION_FROZEN", "EXPIRY_DATE_REQUIRED",
                 "MANUFACTURE_DATE_REQUIRED", "INVALID_STATUS" ->
                    HttpStatus.CONFLICT;
            case "VALIDATION_ERROR", "MISSING_EXPIRY_DATE", "MISSING_MANUFACTURE_DATE" ->
                    HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
