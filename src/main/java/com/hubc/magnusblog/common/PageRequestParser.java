package com.hubc.magnusblog.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Shared parsing of public/admin list pagination: page starts at 0, size in 1..48, default 12. */
public final class PageRequestParser {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;

    private PageRequestParser() {
    }

    public static Pageable parse(String page, String size) {
        int p = parseInt(page, 0);
        int s = parseInt(size, DEFAULT_SIZE);
        if (p < 0) {
            throw new InvalidInputException("page", "页码不能为负数");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw new InvalidInputException("size", "每页数量必须在 1 到 " + MAX_SIZE + " 之间");
        }
        return PageRequest.of(p, s);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException("page", "分页参数格式不正确");
        }
    }
}
