package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import io.github.rkaushikdev.doublerbay.util.Whole;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Taking a day of counter readings off the maintenance system, in bulk.
 *
 * <p>Counters arrive as a file once a night, one line per tail, and the file is never entirely clean.
 * A tail was sold last week and is still on the export. A line has a blank hours column because a
 * technician did not close the sector. One reading runs a counter backwards because the replacement
 * flight data unit started from zero.
 *
 * <p>The wrong shape for this is all or nothing. A file of forty tails refused because one line is
 * wrong means thirty nine aeroplanes with stale counters, and the desk works around it by editing the
 * file, which is worse. So each line is taken on its own and the result says exactly which ones did
 * not land and why.
 *
 * <p>The right shape is also not silence. A line that was skipped has to appear in the answer with
 * its reason, because a skipped line is a counter that will be wrong tomorrow too.
 */
public final class Readings {

    /** The columns this desk's file carries, by header name. */
    public static final List<String> COLUMNS = List.of("serial", "taken", "cycles", "hours");

    private Readings() {}

    /**
     * Read the file, refusing one whose header is not the one this desk sends.
     *
     * <p>The first row is the header. A blank line is skipped and does not count as a line, a column
     * the header names more than once reads from its last place, and a short line reads its missing
     * columns as blank.
     */
    public static List<ReadingLine> linesFrom(String text) {
        List<List<String>> rows = CsvRows.read(text);
        List<String> header = rows.isEmpty() ? List.of() : rows.get(0);
        List<String> missing =
                COLUMNS.stream().filter(one -> !header.contains(one)).toList();
        if (!missing.isEmpty()) {
            throw new BadReading("the file is missing the " + String.join(", ", missing) + " column");
        }
        List<ReadingLine> lines = new ArrayList<>();
        int at = 2;
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.isEmpty()) {
                continue;
            }
            Map<String, String> named = new HashMap<>();
            for (int index = 0; index < Math.min(header.size(), row.size()); index++) {
                named.put(header.get(index), row.get(index));
            }
            lines.add(new ReadingLine(
                    at,
                    column(named, "serial"),
                    column(named, "taken"),
                    column(named, "cycles"),
                    column(named, "hours")));
            at++;
        }
        return List.copyOf(lines);
    }

    private static String column(Map<String, String> named, String name) {
        String held = named.get(name);
        return held == null ? "" : Text.strip(held);
    }

    /** Turn one line into a reading, refusing anything unreadable by name. */
    public static Snapshot snapshotFrom(ReadingLine line) {
        if (line.serial().isEmpty()) {
            throw new BadReading("no serial on the line");
        }
        LocalDate taken;
        try {
            taken = isoDate(line.taken());
        } catch (DateTimeParseException refused) {
            throw new BadReading(Literal.of(line.taken()) + " is not a date");
        }
        Cycles cycles;
        try {
            cycles = new Cycles(Whole.parse(line.cycles()));
        } catch (BadReading | IllegalArgumentException refused) {
            throw new BadReading(Literal.of(line.cycles()) + " is not a count of cycles");
        }
        Hours hours;
        try {
            hours = Hours.ofHours(Decimals.parse(line.hours()));
        } catch (BadReading | IllegalArgumentException | ArithmeticException refused) {
            throw new BadReading(Literal.of(line.hours()) + " is not a count of hours");
        }
        return new Snapshot(taken, new Usage(cycles, hours));
    }

    private static LocalDate isoDate(String text) {
        if (text.length() == 8 && text.chars().allMatch(Character::isDigit)) {
            return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Post every reading a file carries, keeping the ones that will not land.
     *
     * <p>Each line stands on its own. A line is read, and checked against the tail's log, before
     * anything of it is written, so a refusal partway through leaves nothing half written and the
     * thirty nine good lines after it still land.
     */
    public static Taken postAll(UnitOfWork unit, String text) {
        List<Serial> posted = new ArrayList<>();
        List<RefusedLine> refused = new ArrayList<>();
        for (ReadingLine line : linesFrom(text)) {
            try {
                Snapshot snapshot = snapshotFrom(line);
                Serial serial = new Serial(line.serial());
                unit.aircraft().postReading(serial, snapshot);
                unit.flush();
                posted.add(serial);
            } catch (DoublerbayError stopped) {
                refused.add(new RefusedLine(line, stopped.getMessage()));
            }
        }
        return new Taken(posted, refused);
    }
}
