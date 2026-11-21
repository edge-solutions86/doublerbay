package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.service.Measuring;
import io.github.rkaushikdev.doublerbay.service.Orders;
import io.github.rkaushikdev.doublerbay.service.Repairs;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportHistoryTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate AGAIN = LocalDate.of(2025, 9, 1);
    private static final FindingId AGAINST = new FindingId(2025, 1);
    private static final Serial MSN = new Serial("5127");
    private static final RepairId REPAIR = new RepairId(53, 1);
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Dent dent(String depth) {
        return new Dent(Sample.inch(depth), Sample.inch("3.000"), Sample.inch("2.000"));
    }

    private FindingId raised(int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings()
                    .raiseFinding(Sample.cardWith()
                            .sequence(sequence)
                            .geometry(dent("0.020"))
                            .at(Sample.cabinPointOn(10))
                            .build());
            unit.dispositions().save(DispositionRecord.raised(reference));
        });
        return reference;
    }

    private FindingId raised() {
        return raised(1);
    }

    private void assess(FindingId reference) {
        UnitOfWork.run(database, unit -> {
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
    }

    private Story story() {
        return UnitOfWork.apply(database, unit -> History.storyOf(unit, AGAINST));
    }

    private void measuredAgain(FindingId reference) {
        UnitOfWork.run(
                database,
                unit -> Measuring.reMeasure(unit, Book.v320(), new TypeCatalogue(), reference, dent("0.030"), AGAIN));
    }

    private void fit(FindingId reference) {
        Placement placed = Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10)));
        RepairRecord record = new RepairRecord(
                REPAIR,
                new PanelId(53, 10, 143, "L"),
                reference,
                Schemes.selectScheme(Sample.inch("2.000"), Sample.inch("2.000"), placed, Reference.parse("53-40-01")),
                new Offset(Sample.inch("468.000"), Sample.inch("91.000")),
                LocalDate.of(2025, 4, 2),
                UtilisationLog.usage(13900, 24400));
        UnitOfWork.run(database, unit -> Repairs.embody(unit, record, MSN, "s.brandt"));
    }

    @Test
    void aCardThatIsNotOnFileHasNoStory() {
        UnitOfWork.run(database, unit -> assertThatThrownBy(() -> History.storyOf(unit, new FindingId(2025, 9)))
                .isInstanceOf(NotOnFile.class));
    }

    @Test
    void aFreshlyRaisedCardHasOneLine() {
        raised();
        Story built = story();
        assertThat(built.entries()).hasSize(1);
        assertThat(built.entries().get(0).register()).isEqualTo("card");
        assertThat(built.entries().get(0).what()).contains("raised");
    }

    @Test
    void theStoryNamesTheTailItIsOn() {
        raised();
        assertThat(story().serial()).isEqualTo(MSN);
    }

    @Test
    void aMoveLandsOnTheStory() {
        FindingId reference = raised();
        assess(reference);
        Story built = story();
        assertThat(built.entries()).hasSize(2);
        assertThat(built.entries().get(1).register()).isEqualTo("disposition");
    }

    @Test
    void theCardLineReadsBeforeTheMoveOnTheSameDay() {
        FindingId reference = raised();
        assess(reference);
        assertThat(story().entries().stream().map(Entry::register).toList()).containsExactly("card", "disposition");
    }

    @Test
    void aReMeasurementAddsACardLine() {
        FindingId reference = raised();
        assess(reference);
        measuredAgain(reference);
        Story built = story();
        assertThat(built.fromRegister("card")).hasSize(2);
        assertThat(built.entries().get(built.entries().size() - 1).what()).contains("revision 2");
    }

    @Test
    void aDeferralLandsOnTheStory() {
        FindingId reference = raised();
        assess(reference);
        UnitOfWork.run(database, unit -> unit.deferrals()
                .grant(Deferral.grant(
                        reference,
                        DAY,
                        Sample.flown(13800, 24200),
                        "j.mercer",
                        InspectionMethod.DVI,
                        Interval.parse("500 FC"),
                        null,
                        "")));
        Story built = story();
        assertThat(built.fromRegister("deferral")).hasSize(1);
        assertThat(built.fromRegister("deferral").get(0).what()).contains("granted by j.mercer");
    }

    @Test
    void anOrderLandsOnTheStoryTwice() {
        FindingId reference = raised();
        assess(reference);
        UnitOfWork.run(database, unit -> Orders.raiseOrder(unit, reference, QUESTION, DAY, "a.okonkwo"));
        UnitOfWork.run(database, unit -> Orders.send(unit, new OrderId(2025, 1), LocalDate.of(2025, 3, 20)));
        List<Entry> lines = story().fromRegister("engineering");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).what()).contains("raised");
        assertThat(lines.get(1).what()).contains("sent");
    }

    @Test
    void anAnsweredOrderAddsAThirdLine() {
        FindingId reference = raised();
        assess(reference);
        UnitOfWork.run(database, unit -> Orders.raiseOrder(unit, reference, QUESTION, DAY, "a.okonkwo"));
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, new OrderId(2025, 1), LocalDate.of(2025, 3, 20));
            Orders.reject(unit, new OrderId(2025, 1), LocalDate.of(2025, 6, 1), "a.okonkwo");
        });
        assertThat(story().fromRegister("engineering")).hasSize(3);
    }

    @Test
    void aCardWithNoOrdersHasNoEngineeringLines() {
        raised();
        assertThat(story().fromRegister("engineering")).isEmpty();
    }

    @Test
    void aRepairLandsOnTheStory() {
        FindingId reference = raised();
        assess(reference);
        fit(reference);
        List<Entry> lines = story().fromRegister("repair");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).what()).contains("fitted");
    }

    @Test
    void aRemovedRepairAddsASecondLine() {
        FindingId reference = raised();
        assess(reference);
        fit(reference);
        UnitOfWork.run(database, unit -> unit.repairs().remove(REPAIR, LocalDate.of(2026, 1, 5)));
        List<Entry> lines = story().fromRegister("repair");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).what()).contains("removed");
    }

    @Test
    void theStorySpansFromTheFirstDayToTheLast() {
        FindingId reference = raised();
        assess(reference);
        measuredAgain(reference);
        Story built = story();
        assertThat(built.opened()).isEqualTo(DAY);
        assertThat(built.latest()).isEqualTo(AGAIN);
        assertThat(built.daysOpen()).isEqualTo(171);
    }

    @Test
    void aOneDayStorySpansNoDays() {
        raised();
        assertThat(story().daysOpen()).isZero();
    }

    @Test
    void theStoryReadsBackWithACountAndASpan() {
        raised();
        assertThat(story()).hasToString("DR-2025-000001 on MSN 5127: 1 entries over 0 days");
    }

    @Test
    void anEntryReadsBackWithItsRegister() {
        Entry made = new Entry(DAY, "card", "raised");
        assertThat(made).hasToString("2025-03-14 card: raised");
    }

    @Test
    void anUnknownRegisterSortsLast() {
        assertThat(new Entry(DAY, "somewhere else", "x").rank()).isEqualTo(5);
    }

    @Test
    void theRegistersSortInTheOrderTheyHappen() {
        assertThat(new Entry(DAY, "card", "x").rank()).isLessThan(new Entry(DAY, "repair", "x").rank());
    }
}
