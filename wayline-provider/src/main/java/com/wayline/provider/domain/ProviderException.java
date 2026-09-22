package com.wayline.provider.domain;

import com.wayline.common.exception.WaylineException;

/**
 * Exception thrown by payment provider implementations.
 */
public class ProviderException extends WaylineException {

    private final String providerName;
    private final String errorCode;

    public ProviderException(String message, String providerName) {
        super(message);
        this.providerName = providerName;
        this.errorCode = null;
    }

    public ProviderException(String message, String providerName, String errorCode) {
        super(message);
        this.providerName = providerName;
        this.errorCode = errorCode;
    }

    public ProviderException(String message, String providerName, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorCode = null;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
