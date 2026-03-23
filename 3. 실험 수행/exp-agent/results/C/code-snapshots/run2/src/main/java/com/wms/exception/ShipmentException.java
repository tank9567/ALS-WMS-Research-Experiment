package com.wms.exception;

public class ShipmentException extends RuntimeException {
    public ShipmentException(String message) {
        super(message);
    }

    public ShipmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
