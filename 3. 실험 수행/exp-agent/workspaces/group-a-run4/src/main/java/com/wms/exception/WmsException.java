package com.wms.exception;

public class WmsException extends RuntimeException {
    public WmsException(String message) {
        super(message);
    }

    public WmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
