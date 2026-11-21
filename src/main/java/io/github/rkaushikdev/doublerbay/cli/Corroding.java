package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.Counted;
import io.github.rkaushikdev.doublerbay.report.ProgrammeReport;
import io.github.rkaushikdev.doublerbay.report.RepeatLocation;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The corrosion programme, from the terminal.
 *
 * <p>The programme report is the one that goes to the authority once a year, and the unreported
 * list is the one somebody has to look at every morning. They are in the same place because they are
 * the same programme, and a desk that has to remember two commands will look at one of them.
 *
 * <p>The report prints its denominator on the page. A rate with no denominator on it is a number
 * somebody will quote in a meeting without it.
 */
public final class Corroding {

    /** The levels the programme grades, by number. */
    public static final List<String> LEVELS = Arrays.stream(CorrosionLevel.values())
            .map(one -> String.valueOf(one.value()))
            .toList();

    private Corroding() {}

    /**
     * Read a level off its number, refusing one the programme does not grade.
     *
     * @throws Misused for a number that is not one of {@link #LEVELS}
     */
    public static CorrosionLevel levelNumbered(int value) {
        for (CorrosionLevel one : CorrosionLevel.values()) {
            if (one.value() == value) {
                return one;
            }
        }
        throw Misused.badValue(value + " is not one of " + String.join(", ", LEVELS));
    }

    /** The corrosion prevention and control programme. */
    @Command(
            name = "corrosion",
            customSynopsis = "doublerbay corrosion [OPTIONS] COMMAND [ARGS]...",
            description = "The corrosion prevention and control programme.",
            subcommands = {
                CorrosionList.class,
                CorrosionProgramme.class,
                CorrosionReported.class,
                CorrosionUnreported.class,
            })
    static final class Group {}

    /** Graded corrosion findings, on one tail or across the fleet. */
    @Command(
            name = "list",
            customSynopsis = "doublerbay corrosion list [OPTIONS]",
            description = "Graded corrosion findings, on one tail or across the fleet.")
    static final class CorrosionList extends Step {

        @Option(names = "--tail", description = "one tail, or the whole fleet")
        String serial;

        @Option(names = "--level", description = "only findings graded at this level")
        Integer level;

        @Override
        public Integer call() {
            List<CorrosionEntry> entries = work(unit -> {
                List<CorrosionEntry> found;
                if (given(serial)) {
                    Serial held = new Serial(serial);
                    unit.aircraft().get(held);
                    found = unit.corrosion().onTail(held);
                } else {
                    found = unit.corrosion().atLevel(levelNumbered(level == null || level == 0 ? 1 : level));
                }
                if (level != null && given(serial)) {
                    found = found.stream()
                            .filter(one -> one.level().value() == level)
                            .toList();
                }
                return found;
            });
            if (entries.isEmpty()) {
                say(Output.sayNothingFound("graded"));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("part", "level", "found on", "report by"));
            for (CorrosionEntry one : entries) {
                rows.add(List.of(
                        one.part().toString(),
                        String.valueOf(one.level().value()),
                        one.foundOn().toString(),
                        one.reportBy().map(LocalDate::toString).orElse("")));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Level three findings the authority has not been told about yet. */
    @Command(
            name = "unreported",
            customSynopsis = "doublerbay corrosion unreported [OPTIONS]",
            description = "Level three findings the authority has not been told about yet.")
    static final class CorrosionUnreported extends Step {

        @Option(names = "--on", required = true, description = "the day to measure the clock against")
        String onDay;

        @Override
        public Integer call() {
            LocalDate day = Output.asDate(onDay);
            List<CorrosionEntry> entries = work(unit -> unit.corrosion().unreported());
            if (entries.isEmpty()) {
                say(Output.sayNothingFound("waiting to be reported"));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("part", "found on", "report by", "late"));
            for (CorrosionEntry one : entries) {
                rows.add(List.of(
                        one.part().toString(),
                        one.foundOn().toString(),
                        one.reportBy().map(LocalDate::toString).orElse(""),
                        one.isOverdue(day) ? "yes" : "no"));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Record that a level three finding went out to the authority. */
    @Command(
            name = "reported",
            customSynopsis = "doublerbay corrosion reported [OPTIONS] REFERENCE",
            description = "Record that a level three finding went out to the authority.")
    static final class CorrosionReported extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", required = true, description = "the day it went out")
        String onDay;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            inWork(unit -> unit.corrosion().markReported(held, Output.asDate(onDay)));
            say("reported " + held);
            return Main.OK;
        }
    }

    /** One tail's programme picture over a window. */
    @Command(
            name = "programme",
            customSynopsis = "doublerbay corrosion programme [OPTIONS]",
            description = "One tail's programme picture over a window.")
    static final class CorrosionProgramme extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--since", required = true, description = "the window opens")
        String since;

        @Option(names = "--until", required = true, description = "the window closes")
        String until;

        @Override
        public Integer call() {
            Serial held = new Serial(serial);
            List<CorrosionEntry> entries = work(unit -> {
                unit.aircraft().get(held);
                return unit.corrosion().onTail(held);
            });
            ProgrammeReport report = ProgrammeReport.of(held, entries, Output.asDate(since), Output.asDate(until));
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("tail", report.serial().number()));
            rows.add(List.of("window", report.since() + " to " + report.until()));
            rows.add(List.of("graded", String.valueOf(report.total())));
            rows.add(List.of("above level one", String.valueOf(report.aboveLevelOne())));
            rows.add(List.of("escalation rate", Decimals.text(report.escalationRate())));
            rows.add(List.of("over", report.denominator()));
            rows.add(List.of("coping", report.isCoping() ? "yes" : "no"));
            for (Counted<CorrosionLevel> level : report.byLevel()) {
                rows.add(List.of("  level " + level.what().value(), String.valueOf(level.count())));
            }
            for (RepeatLocation one : report.repeats()) {
                rows.add(List.of(
                        "  repeat " + one.part(),
                        one.times() + " times over " + Decimals.text(one.yearsBetween()) + " years"));
            }
            say(Output.columns(rows));
            if (!report.persistent().isEmpty()) {
                say("");
                say(report.persistent().size() + " locations keep corroding, review the programme");
            }
            return Main.OK;
        }
    }
}
