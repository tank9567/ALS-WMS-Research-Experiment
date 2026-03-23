package com.wms.exception;

public class AdjustmentException extends RuntimeException {
    public AdjustmentException(String message) {
        super(message);
    }

    public AdjustmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
