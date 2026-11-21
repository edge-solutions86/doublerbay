package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.util.JsonText;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON laid out for a person to fill in: one member to a line, two spaces of indent per level, a
 * space after every colon, and every character outside ASCII escaped. An empty object or list stays
 * on its own line as {@code {}} or {@code []}.
 */
final class Indented {

    private static final String STEP = "  ";

    private Indented() {}

    /** A value laid out at the outermost level. */
    static String json(Object value) {
        StringBuilder written = new StringBuilder();
        write(value, 0, written);
        return written.toString();
    }

    private static void write(Object value, int depth, StringBuilder written) {
        if (value instanceof Map<?, ?> members) {
            if (members.isEmpty()) {
                written.append("{}");
                return;
            }
            written.append('{');
            Iterator<? extends Map.Entry<?, ?>> each = members.entrySet().iterator();
            while (each.hasNext()) {
                Map.Entry<?, ?> member = each.next();
                newLine(depth + 1, written);
                written.append(JsonText.string(String.valueOf(member.getKey()))).append(": ");
                write(member.getValue(), depth + 1, written);
                if (each.hasNext()) {
                    written.append(',');
                }
            }
            newLine(depth, written);
            written.append('}');
            return;
        }
        if (value instanceof Collection<?> items) {
            if (items.isEmpty()) {
                written.append("[]");
                return;
            }
            written.append('[');
            Iterator<?> each = items.iterator();
            while (each.hasNext()) {
                newLine(depth + 1, written);
                write(each.next(), depth + 1, written);
                if (each.hasNext()) {
                    written.append(',');
                }
            }
            newLine(depth, written);
            written.append(']');
            return;
        }
        written.append(JsonText.value(value));
    }

    private static void newLine(int depth, StringBuilder written) {
        written.append('\n').append(STEP.repeat(depth));
    }
}
