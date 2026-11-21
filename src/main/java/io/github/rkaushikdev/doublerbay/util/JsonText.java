package io.github.rkaushikdev.doublerbay.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * JSON written in one fixed, readable form: a space after every colon and comma, keys in the order
 * they were handed in, and every character outside ASCII escaped, so a line survives any log shipper
 * whatever encoding it assumes (RFC 8259).
 */
public final class JsonText {

    private JsonText() {}

    /** An object, its members in iteration order. Values may be text, numbers, booleans or null. */
    public static String object(Map<String, ?> members) {
        StringBuilder written = new StringBuilder("{");
        Iterator<? extends Map.Entry<String, ?>> each = members.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<String, ?> member = each.next();
            written.append(string(member.getKey())).append(": ").append(value(member.getValue()));
            if (each.hasNext()) {
                written.append(", ");
            }
        }
        return written.append('}').toString();
    }

    /** One value: text, a number, a boolean, null, a nested object or a list. */
    @SuppressWarnings("unchecked")
    public static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return string(text);
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> nested) {
            return object((Map<String, ?>) nested);
        }
        if (value instanceof Collection<?> items) {
            StringBuilder written = new StringBuilder("[");
            Iterator<?> each = items.iterator();
            while (each.hasNext()) {
                written.append(value(each.next()));
                if (each.hasNext()) {
                    written.append(", ");
                }
            }
            return written.append(']').toString();
        }
        return string(value.toString());
    }

    /** Text as a JSON string, with everything outside printable ASCII escaped. */
    public static String string(String text) {
        StringBuilder written = new StringBuilder(text.length() + 2).append('"');
        for (int index = 0; index < text.length(); index++) {
            char at = text.charAt(index);
            switch (at) {
                case '"' -> written.append("\\\"");
                case '\\' -> written.append("\\\\");
                case '\n' -> written.append("\\n");
                case '\r' -> written.append("\\r");
                case '\t' -> written.append("\\t");
                case '\b' -> written.append("\\b");
                case '\f' -> written.append("\\f");
                default -> {
                    if (at < 0x20 || at > 0x7E) {
                        written.append(String.format(Locale.ROOT, "\\u%04x", (int) at));
                    } else {
                        written.append(at);
                    }
                }
            }
        }
        return written.append('"').toString();
    }
}
