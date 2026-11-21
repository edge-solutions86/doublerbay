package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.report.DeskReports;
import io.github.rkaushikdev.doublerbay.report.DueReport;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How many statements a report actually sends, asserted rather than hoped for.
 *
 * <p>The reports on this desk look fast on a test database with four aeroplanes and one deferral
 * each. The shape that hurts is the one that runs a query per row, because it is invisible until the
 * fleet is real and then it is the only thing anybody talks about. So the count is a test: not a
 * threshold on wall clock, which moves with the machine, but the number of statements, which moves
 * only when somebody writes a loop with a query in it.
 */
class QueryCountsTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");
    private static final Pattern FROM_AIRCRAFT = Pattern.compile("^select .* from aircraft\\b.*", Pattern.DOTALL);

    private Database database;

    @BeforeEach
    void oneTail() {
        database = Desk.stocked();
    }

    /** Put a number of deferred cards on the one tail. */
    private static void deferred(Database made, int howMany) {
        for (int step = 1; step <= howMany; step++) {
            FindingId reference = new FindingId(2025, step);
            int sequence = step;
            UnitOfWork.run(made, unit -> {
                unit.findings().raiseFinding(Sample.card(sequence));
                unit.dispositions()
                        .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
            });
            UnitOfWork.run(made, unit -> {
                unit.deferrals()
                        .grant(Deferral.grant(
                                reference,
                                DAY,
                                Sample.flown(13800, 24200),
                                "j.mercer",
                                InspectionMethod.DVI,
                                Interval.parse("500 FC"),
                                null,
                                ""));
                DispositionRecord held = unit.dispositions().get(reference);
                unit.dispositions().save(held.movedTo(DispositionState.DEFERRED, DAY, "j.mercer"));
            });
        }
    }

    private static int dueQueries(Database made, int howMany) {
        deferred(made, howMany);
        return UnitOfWork.apply(made, unit -> made.statementsDuring(() -> DeskReports.dueOnTail(unit, MSN, DAY)))
                .size();
    }

    @Test
    void theCounterSeesAStatementSelectingFromTheAircraftTable() {
        List<String> seen = UnitOfWork.apply(
                database,
                unit -> database.statementsDuring(() -> unit.aircraft().all()));
        assertThat(seen)
                .anyMatch(one ->
                        FROM_AIRCRAFT.matcher(one.toLowerCase(Locale.ROOT)).matches());
    }

    @Test
    void theCounterStopsCountingAfterTheWork() {
        List<String> seen = UnitOfWork.apply(
                database,
                unit -> database.statementsDuring(() -> unit.aircraft().all()));
        int before = seen.size();
        UnitOfWork.run(database, unit -> unit.aircraft().all());
        assertThat(before).isPositive();
        assertThat(seen).hasSize(before);
    }

    @Test
    void oneDeferralTakesAHandfulOfQueries() {
        assertThat(dueQueries(database, 1)).isPositive().isLessThanOrEqualTo(8);
    }

    @Test
    void tenDeferralsTakeTheSameNumberAsOne() {
        int one = dueQueries(database, 1);
        assertThat(dueQueries(Desk.stocked(), 10)).isEqualTo(one);
    }

    @Test
    void twentyDeferralsTakeTheSameNumberAsTen() {
        int ten = dueQueries(database, 10);
        assertThat(dueQueries(Desk.stocked(), 20)).isEqualTo(ten);
    }

    @Test
    void theTailLookupIsOneQueryHoweverManyCards() {
        deferred(database, 10);
        List<FindingId> references = IntStream.rangeClosed(1, 10)
                .mapToObj(step -> new FindingId(2025, step))
                .toList();
        List<Map<FindingId, Serial>> found = new ArrayList<>();
        List<String> seen = UnitOfWork.apply(
                database,
                unit -> database.statementsDuring(
                        () -> found.add(unit.findings().serialsFor(references))));
        assertThat(seen).hasSize(1);
        assertThat(found.get(0)).hasSize(10);
    }

    @Test
    void anEmptySetOfCardsRunsNoQuery() {
        List<Map<FindingId, Serial>> found = new ArrayList<>();
        List<String> seen = UnitOfWork.apply(
                database,
                unit -> database.statementsDuring(
                        () -> found.add(unit.findings().serialsFor(List.of()))));
        assertThat(found.get(0)).isEmpty();
        assertThat(seen).isEmpty();
    }

    @Test
    void aCardThatIsNotOnFileIsSimplyAbsent() {
        deferred(database, 1);
        Map<FindingId, Serial> found = UnitOfWork.apply(
                database, unit -> unit.findings().serialsFor(List.of(new FindingId(2025, 1), new FindingId(2025, 99))));
        assertThat(found).containsKey(new FindingId(2025, 1)).doesNotContainKey(new FindingId(2025, 99));
    }

    @Test
    void theOpenCountDoesNotGrowAQueryPerCard() {
        deferred(database, 2);
        List<String> first = UnitOfWork.apply(
                database, unit -> database.statementsDuring(() -> DeskReports.openAcrossFleet(unit, DAY)));
        Database made = Desk.stocked();
        deferred(made, 12);
        List<String> second =
                UnitOfWork.apply(made, unit -> made.statementsDuring(() -> DeskReports.openAcrossFleet(unit, DAY)));
        assertThat(first).isNotEmpty();
        assertThat(second).hasSameSizeAs(first);
    }

    @Test
    void theDueListStillFindsTheRightCards() {
        deferred(database, 3);
        DueReport report = UnitOfWork.apply(database, unit -> DeskReports.dueOnTail(unit, MSN, DAY));
        assertThat(report.items()).hasSize(3);
    }

    @Test
    void anotherTailsDeferralsAreStillLeftOut() {
        deferred(database, 2);
        Desk.enrolled(database, "5240");
        DueReport report = UnitOfWork.apply(database, unit -> DeskReports.dueOnTail(unit, new Serial("5240"), DAY));
        assertThat(report.items()).isEmpty();
    }
}
