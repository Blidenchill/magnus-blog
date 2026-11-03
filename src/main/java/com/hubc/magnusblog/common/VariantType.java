package com.hubc.magnusblog.common;

public enum VariantType {
    ORIGINAL, DISPLAY, THUMB;

    public static VariantType fromPathSegment(String segment) {
        return switch (segment == null ? "" : segment.toLowerCase(java.util.Locale.ROOT)) {
            case "original" -> ORIGINAL;
            case "display" -> DISPLAY;
            case "thumb" -> THUMB;
            default -> throw new IllegalArgumentException("unknown variant: " + segment);
        };
    }
}
