package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.assess.Assessment;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.assess.PanelAssessment;
import io.github.rkaushikdev.doublerbay.assess.Reason;
import io.github.rkaushikdev.doublerbay.assess.Verdict;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Asking the book what happens to a card, from the terminal.
 *
 * <p>This is the command the desk runs most. It takes a reference and a day and it prints the
 * disposition with every reason that got it there, passed and failed both, because an engineer who
 * is going to sign against the answer wants to see which line of which page it came off.
 *
 * <p>The day is an argument rather than now for the same reason the HTTP route takes it. What this
 * was assessed as in March and what it would be assessed as today are different questions once a
 * revision has landed in between, and the desk asks both.
 */
public final class Assessing {

    /** The mark on a rule that passed. */
    public static final String TICK = "ok";

    /** The mark on a rule that failed. */
    public static final String CROSS = "no";

    /** What a citation prints for a revision it was not given. */
    private static final String NO_REVISION = "None";

    private Assessing() {}

    /** The disposition, where it came from, and every reason under it. */
    public static List<List<String>> verdictLines(Assessment one) {
        Verdict verdict = one.verdict();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("card", one.finding().reference().toString()));
        rows.add(List.of("part", one.placement().part().toString()));
        rows.add(List.of("region", one.placement().region().toString()));
        rows.add(List.of("thickness", one.placement().thickness().toString()));
        rows.add(List.of("disposition", verdict.disposition().value()));
        rows.add(List.of("why", verdict.why()));
        verdict.reference()
                .ifPresent(page -> rows.add(List.of(
                        "page",
                        page + " rev " + verdict.revision().map(String::valueOf).orElse(NO_REVISION))));
        for (Reason reason : verdict.reasons()) {
            rows.add(List.of("  " + (reason.passed() ? TICK : CROSS) + " " + reason.rule(), reason.detail()));
        }
        return rows;
    }

    /** What happens to one card, and every reason behind it. */
    @Command(
            name = "assess",
            customSynopsis = "doublerbay assess [OPTIONS] REFERENCE",
            description = "What happens to one card, and every reason behind it.")
    static final class AssessCard extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", description = "assess it as the book stood on this day")
        String onDay;

        @Override
        public Integer call() {
            Book book = Book.v320();
            TypeCatalogue catalogue = new TypeCatalogue();
            Assessment answer = work(unit -> {
                Finding finding = unit.findings().latest(FindingId.parse(reference));
                LocalDate day = given(onDay) ? Output.asDate(onDay) : finding.foundOn();
                TailContext tail = StoreContext.contextFor(unit, catalogue, finding.serial(), day);
                return Assessor.assess(book, tail, finding);
            });
            say(Output.columns(verdictLines(answer)));
            return Main.OK;
        }
    }

    /** Every open card on one tail, panel by panel, worst first. */
    @Command(
            name = "assess-tail",
            customSynopsis = "doublerbay assess-tail [OPTIONS] SERIAL",
            description = "Every open card on one tail, panel by panel, worst first.")
    static final class AssessTail extends Step {

        @Parameters(index = "0", paramLabel = "SERIAL")
        String serial;

        @Option(names = "--on", required = true, description = "the day to assess against")
        String onDay;

        @Override
        public Integer call() {
            Book book = Book.v320();
            TypeCatalogue catalogue = new TypeCatalogue();
            Serial held = new Serial(serial);
            LocalDate day = given(onDay) ? Output.asDate(onDay) : null;
            List<PanelAssessment> panels = work(unit -> {
                TailContext tail = day != null ? StoreContext.contextFor(unit, catalogue, held, day) : null;
                List<Finding> cards = unit.findings().openOn(held);
                if (tail == null) {
                    throw Misused.because("assessing a tail needs a day");
                }
                return cards.isEmpty() ? List.of() : PanelAssessment.assessByPanel(book, tail, cards);
            });

            if (panels.isEmpty()) {
                say(Output.sayNothingFound("open on " + serial));
                return Main.OK;
            }

            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("panel", "cards", "damages", "disposition"));
            List<Assessment> every = new ArrayList<>();
            for (PanelAssessment panel : panels) {
                rows.add(List.of(
                        panel.part().toString(),
                        String.valueOf(panel.cardCount()),
                        String.valueOf(panel.damageCount()),
                        panel.disposition().value()));
                every.addAll(panel.assessments());
            }
            rows.add(List.of("", "", "", ""));
            rows.add(List.of(
                    "whole tail", "", "", Assessor.worstDisposition(every).value()));
            say(Output.columns(rows));
            return Main.OK;
        }
    }
}
