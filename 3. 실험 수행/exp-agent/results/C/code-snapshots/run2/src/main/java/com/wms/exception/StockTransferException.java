package com.wms.exception;

import lombok.Getter;

@Getter
public class StockTransferException extends RuntimeException {
    private final String errorCode;

    public StockTransferException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
