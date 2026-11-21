package io.github.rkaushikdev.doublerbay.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma separated text read the way a spreadsheet writes it.
 *
 * <p>Fields are split on commas. A field may be wrapped in double quotes, inside which a comma, a
 * line break or a doubled quote is part of the value. A quote that closes a field and is followed by
 * more characters keeps those characters in the field, which is how a spreadsheet reads a stray
 * quote. A blank line reads as a row with no fields at all, which the caller decides how to treat.
 */
final class CsvRows {

    private enum State {
        START_RECORD,
        START_FIELD,
        IN_FIELD,
        IN_QUOTED_FIELD,
        QUOTE_IN_QUOTED_FIELD,
        EAT_LINE_END
    }

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';

    private final List<List<String>> rows = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private final StringBuilder field = new StringBuilder();
    private State state = State.START_RECORD;

    private CsvRows() {}

    /**
     * Every row the text carries, in order.
     *
     * @throws IllegalArgumentException when a line break stands inside a field that is not quoted
     */
    static List<List<String>> read(String text) {
        CsvRows reader = new CsvRows();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            int stop = end < 0 ? text.length() : end + 1;
            reader.line(text, start, stop);
            start = stop;
        }
        reader.finish();
        return List.copyOf(reader.rows);
    }

    /** One physical line, its own line break included, then the end of the line. */
    private void line(String text, int start, int stop) {
        for (int at = start; at < stop; at++) {
            character(text.charAt(at));
        }
        endOfLine();
        if (state == State.START_RECORD) {
            rows.add(List.copyOf(fields));
            fields = new ArrayList<>();
        }
    }

    /** The end of the text, which closes a quoted field left open rather than losing it. */
    private void finish() {
        if (field.length() != 0 || state == State.IN_QUOTED_FIELD) {
            saveField();
            rows.add(List.copyOf(fields));
            fields = new ArrayList<>();
        }
    }

    private void character(char at) {
        switch (state) {
            case START_RECORD -> {
                if (at == '\n' || at == '\r') {
                    state = State.EAT_LINE_END;
                    return;
                }
                state = State.START_FIELD;
                startField(at);
            }
            case START_FIELD -> startField(at);
            case IN_FIELD -> {
                if (at == '\n' || at == '\r') {
                    saveField();
                    state = State.EAT_LINE_END;
                } else if (at == DELIMITER) {
                    saveField();
                    state = State.START_FIELD;
                } else {
                    field.append(at);
                }
            }
            case IN_QUOTED_FIELD -> {
                if (at == QUOTE) {
                    state = State.QUOTE_IN_QUOTED_FIELD;
                } else {
                    field.append(at);
                }
            }
            case QUOTE_IN_QUOTED_FIELD -> {
                if (at == QUOTE) {
                    field.append(at);
                    state = State.IN_QUOTED_FIELD;
                } else if (at == DELIMITER) {
                    saveField();
                    state = State.START_FIELD;
                } else if (at == '\n' || at == '\r') {
                    saveField();
                    state = State.EAT_LINE_END;
                } else {
                    field.append(at);
                    state = State.IN_FIELD;
                }
            }
            case EAT_LINE_END -> {
                if (at != '\n' && at != '\r') {
                    throw new IllegalArgumentException("new-line character seen in unquoted field");
                }
            }
        }
    }

    private void startField(char at) {
        if (at == '\n' || at == '\r') {
            saveField();
            state = State.EAT_LINE_END;
        } else if (at == QUOTE) {
            state = State.IN_QUOTED_FIELD;
        } else if (at == DELIMITER) {
            saveField();
        } else {
            field.append(at);
            state = State.IN_FIELD;
        }
    }

    private void endOfLine() {
        switch (state) {
            case START_RECORD, IN_QUOTED_FIELD -> {
                // A blank line stays a blank row, and a quoted field carries on onto the next line.
            }
            case START_FIELD, IN_FIELD, QUOTE_IN_QUOTED_FIELD -> {
                saveField();
                state = State.START_RECORD;
            }
            case EAT_LINE_END -> state = State.START_RECORD;
        }
    }

    private void saveField() {
        fields.add(field.toString());
        field.setLength(0);
    }
}
