package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReportDamageTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");

    private static final Map<DamageKind, Supplier<Geometry>> GEOMETRY = Map.of(
            DamageKind.DENT, () -> new Dent(Sample.inch("0.020"), Sample.inch("3.000"), Sample.inch("2.000")),
            DamageKind.CRACK, () -> new Crack(Sample.inch("0.400"), new Angle(0)),
            DamageKind.CORROSION,
                    () -> new Corrosion(Sample.inch("2.000"), Sample.inch("1.000"), Sample.inch("0.004")));

    private static final Map<DamageKind, DamageOrigin> ORIGIN = Map.of(
            DamageKind.DENT, DamageOrigin.IMPACT,
            DamageKind.CRACK, DamageOrigin.FATIGUE,
            DamageKind.CORROSION, DamageOrigin.ENVIRONMENT);

    private static Finding card(int sequence, DamageKind kind, LocalDate found, DamageOrigin origin, Point at) {
        return Sample.cardWith()
                .sequence(sequence)
                .kind(kind)
                .geometry(GEOMETRY.get(kind).get())
                .at(at)
                .found(found)
                .origin(origin != null ? origin : ORIGIN.get(kind))
                .build();
    }

    private static Finding card(int sequence, DamageKind kind) {
        return card(sequence, kind, DAY, null, null);
    }

    private static Finding card(int sequence, LocalDate found) {
        return card(sequence, DamageKind.DENT, found, null, null);
    }

    private static Finding card(int sequence, DamageOrigin origin) {
        return card(sequence, DamageKind.DENT, DAY, origin, null);
    }

    private static Finding card(int sequence) {
        return card(sequence, DamageKind.DENT);
    }

    private static Finding card() {
        return card(1);
    }

    private static Finding wingCard(int sequence) {
        return card(sequence, DamageKind.CORROSION, DAY, null, Sample.lowerWingPoint());
    }

    private static Finding wingCard() {
        return wingCard(9);
    }

    private static Map<Serial, List<Finding>> holdings(
            Serial first, List<Finding> held, Serial second, List<Finding> more) {
        Map<Serial, List<Finding>> holdings = new LinkedHashMap<>();
        holdings.put(first, held);
        holdings.put(second, more);
        return holdings;
    }

    @Test
    void aFuselageCardFilesUnderFiftyThree() {
        assertThat(DamageCount.chapterOf(card())).isEqualTo(DamageCount.FUSELAGE_CHAPTER);
    }

    @Test
    void aWingCardFilesUnderFiftySeven() {
        assertThat(DamageCount.chapterOf(wingCard())).isEqualTo(DamageCount.WING_CHAPTER);
    }

    @Test
    void aTailWithNothingOpenIsClean() {
        DamageCount counted = DamageCount.countOpen(MSN, List.of(), DAY);
        assertThat(counted.isClean()).isTrue();
        assertThat(counted.worstKind()).isEmpty();
        assertThat(counted).hasToString("MSN 5127: nothing open");
    }

    @Test
    void openCardsAreCounted() {
        DamageCount counted = DamageCount.countOpen(MSN, List.of(card(1), card(2)), DAY);
        assertThat(counted.total()).isEqualTo(2);
        assertThat(counted.isClean()).isFalse();
    }

    @Test
    void aSupersededCardIsNotCounted() {
        Finding carried = card(1).withSupersededBy(new FindingId(2025, 2));
        assertThat(DamageCount.countOpen(MSN, List.of(carried, card(2)), DAY).total())
                .isEqualTo(1);
    }

    @Test
    void aCardFoundAfterTheDayAskedForIsNotCounted() {
        Finding later = card(2, LocalDate.of(2025, 6, 1));
        assertThat(DamageCount.countOpen(MSN, List.of(card(1), later), DAY).total())
                .isEqualTo(1);
    }

    @Test
    void theKindsAreCountedMostFirst() {
        List<Finding> found = List.of(card(1), card(2), card(3, DamageKind.CRACK));
        DamageCount counted = DamageCount.countOpen(MSN, found, DAY);
        assertThat(counted.byKind().get(0)).isEqualTo(new Counted<>(DamageKind.DENT, 2));
        assertThat(counted.worstKind()).contains(DamageKind.DENT);
    }

    @Test
    void aTieBetweenKindsBreaksByName() {
        List<Finding> found = List.of(card(1, DamageKind.DENT), card(2, DamageKind.CRACK));
        DamageCount counted = DamageCount.countOpen(MSN, found, DAY);
        assertThat(counted.byKind().stream().map(Counted::what)).containsExactly(DamageKind.CRACK, DamageKind.DENT);
    }

    @Test
    void aKindThatIsNotThereCountsZero() {
        assertThat(DamageCount.countOpen(MSN, List.of(card(1)), DAY).ofKind(DamageKind.CRACK))
                .isZero();
    }

    @Test
    void theChaptersAreCountedApart() {
        DamageCount counted = DamageCount.countOpen(MSN, List.of(card(1), wingCard(2)), DAY);
        assertThat(counted.inChapter(DamageCount.FUSELAGE_CHAPTER)).isEqualTo(1);
        assertThat(counted.inChapter(DamageCount.WING_CHAPTER)).isEqualTo(1);
    }

    @Test
    void aChapterWithNothingInItCountsZero() {
        assertThat(DamageCount.countOpen(MSN, List.of(card(1)), DAY).inChapter(DamageCount.WING_CHAPTER))
                .isZero();
    }

    @Test
    void theOriginsAreCounted() {
        List<Finding> found = List.of(card(1), card(2, DamageOrigin.HANDLING));
        assertThat(Counted.countOf(DamageCount.countOpen(MSN, found, DAY).byOrigin(), DamageOrigin.HANDLING))
                .isEqualTo(1);
    }

    @Test
    void theRecentWindowCountsOnlyWhatLandedInsideIt() {
        Finding old = card(1, LocalDate.of(2024, 12, 1));
        DamageCount counted = DamageCount.countOpen(MSN, List.of(old, card(2)), DAY);
        assertThat(counted.total()).isEqualTo(2);
        assertThat(counted.raisedRecently()).isEqualTo(1);
    }

    @Test
    void theRecentWindowCanBeWidened() {
        Finding old = card(1, LocalDate.of(2024, 12, 1));
        assertThat(DamageCount.countOpen(MSN, List.of(old, card(2)), DAY, 365).raisedRecently())
                .isEqualTo(2);
    }

    @Test
    void aCardLandingExactlyOnTheWindowEdgeIsInsideIt() {
        Finding edge = card(1, LocalDate.of(2025, 2, 12));
        assertThat(DamageCount.countOpen(MSN, List.of(edge), DAY, 30).raisedRecently())
                .isEqualTo(1);
    }

    @Test
    void aWindowOfNoDaysIsRefused() {
        assertThatThrownBy(() -> DamageCount.countOpen(MSN, List.of(), DAY, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least a day");
    }

    @Test
    void aTailReadsBackWithItsWorstKind() {
        DamageCount counted = DamageCount.countOpen(MSN, List.of(card(1), card(2)), DAY);
        assertThat(counted).hasToString("MSN 5127: 2 open, worst dent");
    }

    @Test
    void theFleetReportPutsTheWorstTailFirst() {
        FleetDamage report =
                FleetDamage.countFleet(holdings(MSN, List.of(card(1)), OTHER, List.of(card(2), card(3))), DAY);
        assertThat(report.tails().get(0).serial()).isEqualTo(OTHER);
        assertThat(report.total()).isEqualTo(3);
    }

    @Test
    void twoTailsWithTheSameCountSortBySerial() {
        FleetDamage report = FleetDamage.countFleet(holdings(OTHER, List.of(card(1)), MSN, List.of(card(2))), DAY);
        assertThat(report.tails().stream().map(DamageCount::serial)).containsExactly(MSN, OTHER);
    }

    @Test
    void aCleanTailIsNamedAsClean() {
        FleetDamage report = FleetDamage.countFleet(holdings(MSN, List.of(card(1)), OTHER, List.of()), DAY);
        assertThat(report.cleanTails()).containsExactly(OTHER);
    }

    @Test
    void aFleetWithNothingOpenHasNoWorstTail() {
        FleetDamage report = FleetDamage.countFleet(holdings(MSN, List.of(), OTHER, List.of()), DAY);
        assertThat(report.worstTail()).isEmpty();
        assertThat(report.total()).isZero();
    }

    @Test
    void aTailCanBePickedOutOfTheReport() {
        FleetDamage report = FleetDamage.countFleet(holdings(MSN, List.of(card(1)), OTHER, List.of()), DAY);
        assertThat(report.forTail(OTHER).isClean()).isTrue();
    }

    @Test
    void aTailThatIsNotInTheReportIsRefused() {
        FleetDamage report = FleetDamage.countFleet(Map.of(MSN, List.of(card(1))), DAY);
        assertThatThrownBy(() -> report.forTail(OTHER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not in this report");
    }

    @Test
    void theFleetReportReadsBackAsOneLine() {
        FleetDamage report = FleetDamage.countFleet(holdings(MSN, List.of(card(1)), OTHER, List.of()), DAY);
        assertThat(report).hasToString("2 tails, 1 open on 2025-03-14");
    }

    @Test
    void anEmptyRegisterReportsNothing() {
        FleetDamage report = FleetDamage.countFleet(Map.of(), DAY);
        assertThat(report.tails()).isEmpty();
        assertThat(report.total()).isZero();
    }
}
