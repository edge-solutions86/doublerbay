package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading arguments and laying out answers, kept apart from the commands.
 *
 * <p>Two commands in different classes both want to print a table, and the one that reaches into
 * the other to get it creates a cycle the moment the first one grows a subcommand. So the small
 * shared pieces live here and nothing here uses a command.
 *
 * <p>The layout is deliberately plain columns rather than box drawing. The output of these commands
 * gets pasted into an email to a lessor, and a table made of unicode corners does not survive that
 * trip.
 */
public final class Output {

    /** What goes between two columns. */
    static final String GAP = "  ";

    /**
     * A date written year, month and day: four digits of year, then a month and a day of one or two
     * digits, a single digit day allowed to be padded with a space instead of a zero.
     */
    private static final Pattern DATE =
            Pattern.compile("([0-9]{4})-(1[0-2]|0[1-9]|[1-9])-(3[01]|[12][0-9]|0[1-9]|[1-9]| [1-9])");

    private Output() {}

    /** Read a date the one way, {@code yyyy-mm-dd}, refusing anything else by name. */
    public static LocalDate asDate(String text) {
        Matcher match = DATE.matcher(text);
        if (match.matches()) {
            int year = Integer.parseInt(match.group(1));
            int month = Integer.parseInt(match.group(2));
            int day = Integer.parseInt(Text.strip(match.group(3)));
            if (year >= 1) {
                try {
                    return LocalDate.of(year, month, day);
                } catch (DateTimeException impossible) {
                    throw notADate(text);
                }
            }
        }
        throw notADate(text);
    }

    private static BadReading notADate(String text) {
        return new BadReading(Literal.of(text) + " is not a date, write it as yyyy-mm-dd");
    }

    /**
     * Lay rows out in columns wide enough for what is in them.
     *
     * <p>A short row is padded out with empty cells, every column is as wide as its widest cell with
     * two spaces between columns, and each line has its trailing white space taken off. No rows lay
     * out as nothing.
     */
    public static String columns(Iterable<? extends Iterable<String>> rows) {
        List<List<String>> laid = new ArrayList<>();
        for (Iterable<String> row : rows) {
            List<String> cells = new ArrayList<>();
            row.forEach(cells::add);
            laid.add(cells);
        }
        if (laid.isEmpty()) {
            return "";
        }
        int width = 0;
        for (List<String> row : laid) {
            width = Math.max(width, row.size());
        }
        for (List<String> row : laid) {
            while (row.size() < width) {
                row.add("");
            }
        }
        int[] widths = new int[width];
        for (List<String> row : laid) {
            for (int index = 0; index < width; index++) {
                widths[index] = Math.max(widths[index], row.get(index).length());
            }
        }
        List<String> lines = new ArrayList<>();
        for (List<String> row : laid) {
            List<String> cells = new ArrayList<>();
            for (int index = 0; index < width; index++) {
                cells.add(Text.padRight(row.get(index), widths[index]));
            }
            lines.add(Text.stripTrailing(String.join(GAP, cells)));
        }
        return String.join("\n", lines);
    }

    /** What to print when a report has no rows, which is not an error: {@code nothing open on 5127}. */
    public static String sayNothingFound(String what) {
        return "nothing " + what;
    }
}
