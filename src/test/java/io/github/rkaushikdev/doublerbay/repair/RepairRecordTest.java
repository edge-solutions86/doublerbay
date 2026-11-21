package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.DoublerForm;
import io.github.rkaushikdev.doublerbay.structure.InstalledDoubler;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepairRecordTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate EMBODIED = LocalDate.of(2025, 4, 2);
    private static final String[] CENTRE = {"468.000", "91.000"};

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static Placement placed(Point point) {
        return Placement.place(Sample.tailContext(), Sample.card(point != null ? point : Sample.cabinPointOn(10)));
    }

    private static RepairRecord record(int sequence, String[] centre, String along, LocalDate removed, Point point) {
        return RepairRecord.of(
                new RepairId(53, sequence),
                new PanelId(53, 10, 143, "L"),
                new FindingId(2025, sequence),
                Schemes.selectScheme(inch(along), inch("2.000"), placed(point), PAGE),
                at(centre[0], centre[1]),
                EMBODIED,
                UtilisationLog.usage(13900, 24400),
                removed);
    }

    private static RepairRecord record() {
        return record(1, CENTRE, "2.000", null, null);
    }

    private static RepairRecord record(int sequence, String along, String around) {
        return record(sequence, new String[] {along, around}, "2.000", null, null);
    }

    private static RepairRecord removed(LocalDate on) {
        return record(1, CENTRE, "2.000", on, null);
    }

    private static RepairRecord at(Point point) {
        return record(1, CENTRE, "2.000", null, point);
    }

    @Test
    void aRepairReadsBackWhereItIsAndWhatItCommittedTo() {
        assertThat(record()).hasToString("RP-53-0001 on 5310-143L, category A, fitted");
        assertThat(removed(LocalDate.of(2026, 1, 1)).toString()).endsWith("removed");
    }

    @Test
    void aRepairRemovedBeforeItWentOnIsRefused() {
        assertThatThrownBy(() -> removed(LocalDate.of(2024, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("removed before it went on");
    }

    @Test
    void aFittedRepairIsFittedUntilItIsTakenOff() {
        assertThat(record().isFitted()).isTrue();
        assertThat(removed(LocalDate.of(2026, 1, 1)).isFitted()).isFalse();
    }

    @Test
    void theFootprintIsThePatchAndNotTheDamage() {
        RepairRecord made = record(1, CENTRE, "2.000", null, null);
        assertThat(made.footprint().along()).isEqualTo(made.scheme().doubler().along());
        assertThat(made.footprint().along().isGreaterThan(inch("2.000"))).isTrue();
    }

    @Test
    void aCategoryARepairCarriesNoInspection() {
        assertThat(record().category()).isSameAs(RepairCategory.A);
        assertThat(record().needsInspection()).isFalse();
    }

    @Test
    void aWingRepairCarriesAnInspectionWhileItIsFitted() {
        RepairRecord wing = at(Sample.lowerWingPoint());
        assertThat(wing.category()).isSameAs(RepairCategory.B);
        assertThat(wing.needsInspection()).isTrue();
    }

    @Test
    void aRemovedRepairNeedsNoMoreInspecting() {
        RepairRecord wing = record(1, CENTRE, "2.000", LocalDate.of(2026, 1, 1), Sample.lowerWingPoint());
        assertThat(wing.category()).isSameAs(RepairCategory.B);
        assertThat(wing.needsInspection()).isFalse();
    }

    @Test
    void theRecordHandsItselfToTheStructureAsAnInstalledDoubler() {
        InstalledDoubler fitted = record().asInstalled();
        assertThat(fitted.part()).isEqualTo("RP-53-0001");
        assertThat(fitted.form()).isSameAs(DoublerForm.EXTERNAL);
        assertThat(fitted.gauge()).isEqualTo(record().scheme().doubler().gauge());
    }

    @Test
    void aFlushRepairHandsItselfOverAsAFlushDoubler() {
        RepairRecord crown = at(Sample.cabinPointOn(2));
        assertThat(crown.scheme().form()).isSameAs(RepairForm.FLUSH);
        assertThat(crown.asInstalled().form()).isSameAs(DoublerForm.FLUSH);
    }

    @Test
    void theClearanceAskedForComesOffTheFastenerSize() {
        RepairRecord made = record();
        assertThat(made.requiredClearance())
                .isEqualTo(made.scheme().doubler().size().diameters("4.0"));
    }

    @Test
    void twoRepairsFarApartClearEachOther() {
        RepairRecord first = record(1, "400.000", "91.000");
        RepairRecord second = record(2, "500.000", "91.000");
        assertThat(first.clears(second)).isTrue();
        assertThat(first.clearanceTo(second).isGreaterThan(first.requiredClearance()))
                .isTrue();
    }

    @Test
    void twoRepairsOnTopOfEachOtherDoNotClear() {
        RepairRecord first = record(1, "468.000", "91.000");
        RepairRecord second = record(2, "469.000", "91.000");
        assertThat(first.clears(second)).isFalse();
        assertThat(first.clearanceTo(second).mils()).isZero();
    }

    @Test
    void theClearanceIsMeasuredBetweenThePatchesAndNotTheDamages() {
        RepairRecord first = record(1, new String[] {"468.000", "91.000"}, "2.000", null, null);
        RepairRecord second = record(2, new String[] {"474.000", "91.000"}, "2.000", null, null);
        assertThat(first.clearanceTo(second).isLessThan(inch("4.000"))).isTrue();
    }

    @Test
    void theLargerOfTheTwoClearancesGoverns() {
        RepairRecord first = record(1, "400.000", "91.000");
        RepairRecord second = record(2, "410.000", "91.000");
        Length needed = Length.max(first.requiredClearance(), second.requiredClearance());
        assertThat(first.clears(second)).isEqualTo(first.clearanceTo(second).isAtLeast(needed));
    }

    @Test
    void aClashSweepFindsThePairsThatAreTooClose() {
        List<RepairRecord> close = List.of(record(1, "468.000", "91.000"), record(2, "469.000", "91.000"));
        assertThat(RepairRecord.clashes(close)).hasSize(1);
    }

    @Test
    void aClashSweepIgnoresRepairsThatHaveBeenTakenOff() {
        List<RepairRecord> mixed = List.of(
                record(1, "468.000", "91.000"),
                record(2, new String[] {"469.000", "91.000"}, "2.000", LocalDate.of(2026, 1, 1), null));
        assertThat(RepairRecord.clashes(mixed)).isEqualTo(List.of());
    }

    @Test
    void aClashSweepOverRepairsThatClearFindsNothing() {
        List<RepairRecord> apart = List.of(record(1, "400.000", "91.000"), record(2, "500.000", "91.000"));
        assertThat(RepairRecord.clashes(apart)).isEqualTo(List.of());
    }

    @Test
    void takingARepairOffLeavesTheEarlierRecordAlone() {
        RepairRecord before = record();
        RepairRecord after = before.removedOn(LocalDate.of(2026, 1, 1));
        assertThat(after.removed()).contains(LocalDate.of(2026, 1, 1));
        assertThat(before.removed()).isEmpty();
    }
}
