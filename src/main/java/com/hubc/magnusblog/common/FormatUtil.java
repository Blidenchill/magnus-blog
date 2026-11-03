package com.hubc.magnusblog.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Template-facing date formatting; all instants are stored in UTC and shown in Asia/Shanghai. */
@Component("fmt")
public class FormatUtil {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZONE));
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(ZONE));
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }
}
