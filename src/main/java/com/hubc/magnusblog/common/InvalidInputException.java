package com.hubc.magnusblog.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bean/form validation failure. Maps to 422 with per-field messages. */
public class InvalidInputException extends RuntimeException {

    private final Map<String, String> fieldErrors = new LinkedHashMap<>();

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String field, String message) {
        super(message);
        fieldErrors.put(field, message);
    }

    public InvalidInputException(Map<String, String> fieldErrors) {
        super("validation failed");
        this.fieldErrors.putAll(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
