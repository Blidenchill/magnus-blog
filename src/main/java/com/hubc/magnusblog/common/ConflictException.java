package com.hubc.magnusblog.common;

/** Optimistic-lock or state conflict. Maps to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
