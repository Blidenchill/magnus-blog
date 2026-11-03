package com.hubc.magnusblog.common;

/** Resource does not exist or is not visible to the current caller. Maps to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
