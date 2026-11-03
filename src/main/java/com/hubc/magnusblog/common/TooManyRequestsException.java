package com.hubc.magnusblog.common;

/** Rate limit / busy. Maps to 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
