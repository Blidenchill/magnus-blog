package com.hubc.magnusblog.common;

import java.util.Locale;

public enum ContentType {
    PHOTO, BLOG, FOOD, TRAVEL;

    public static ContentType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("type is required");
        }
        try {
            return ContentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown content type: " + value);
        }
    }
}
