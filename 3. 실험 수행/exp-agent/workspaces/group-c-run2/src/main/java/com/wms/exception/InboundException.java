package com.wms.exception;

public class InboundException extends RuntimeException {
    private final String errorCode;

    public InboundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
