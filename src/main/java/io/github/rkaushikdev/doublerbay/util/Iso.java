package io.github.rkaushikdev.doublerbay.util;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Moments written in ISO 8601, the one way: seconds always present, a fraction only when there is
 * one (to the microsecond), and the offset written as hours and minutes ({@code +00:00}).
 */
public final class Iso {

    private Iso() {}

    /** A moment with its offset, e.g. {@code 2025-03-14T06:30:00+00:00}. */
    public static String of(OffsetDateTime moment) {
        return moment.toLocalDate() + "T" + of(moment.toLocalTime()) + offset(moment.getOffset());
    }

    /** A time of day, e.g. {@code 06:30:00} or {@code 06:30:00.250000}. */
    public static String of(LocalTime time) {
        String whole = String.format(Locale.ROOT, "%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
        int micros = time.getNano() / 1000;
        return micros == 0 ? whole : whole + String.format(Locale.ROOT, ".%06d", micros);
    }

    private static String offset(ZoneOffset offset) {
        int seconds = offset.getTotalSeconds();
        char sign = seconds < 0 ? '-' : '+';
        int magnitude = Math.abs(seconds);
        String written = String.format(Locale.ROOT, "%c%02d:%02d", sign, magnitude / 3600, magnitude / 60 % 60);
        return magnitude % 60 == 0 ? written : written + String.format(Locale.ROOT, ":%02d", magnitude % 60);
    }
}
