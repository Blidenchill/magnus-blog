package com.hubc.magnusblog.common;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class SlugUtil {

    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?$");

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SlugUtil() {
    }

    public static boolean isValid(String slug) {
        return slug != null && SLUG_PATTERN.matcher(slug).matches();
    }

    /** Generate a random slug that is valid until the author picks a nicer one before first publish. */
    public static String randomSlug(String prefix) {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(prefix).append('-');
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        }
        return sb.toString();
    }

    /** Normalize user input into slug shape; returns null when nothing usable remains. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty() || s.length() > 80) {
            return null;
        }
        return s;
    }
}
