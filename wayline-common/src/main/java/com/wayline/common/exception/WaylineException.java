package com.wayline.common.exception;

/**
 * Base exception for all Wayline business exceptions.
 */
public class WaylineException extends RuntimeException {
    public WaylineException(String message) {
        super(message);
    }

    public WaylineException(String message, Throwable cause) {
        super(message, cause);
    }
}
