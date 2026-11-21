package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Buckle;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Disbond;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.damage.LightningBurn;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Bulletin;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingTest {

    private static final FindingId DR = new FindingId(2025, 4182);

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Angle deg(long value) {
        return Angle.ofDegrees(value);
    }

    private static final Map<DamageKind, Geometry> EVERY_GEOMETRY = everyGeometry();

    private static Map<DamageKind, Geometry> everyGeometry() {
        Map<DamageKind, Geometry> held = new LinkedHashMap<>();
        held.put(DamageKind.DENT, new Dent(inch("0.020"), inch("3.000"), inch("2.000"), true, false));
        held.put(DamageKind.SCRATCH, new Scoring(inch("0.003"), inch("2.000"), inch("0.060"), deg(45), true));
        held.put(DamageKind.GOUGE, new Scoring(inch("0.006"), inch("1.000"), inch("0.090"), deg(90)));
        held.put(DamageKind.NICK, new Scoring(inch("0.004"), inch("0.250"), inch("0.060"), deg(0)));
        held.put(DamageKind.CRACK, new Crack(inch("0.750"), deg(10), false, true, 1));
        held.put(DamageKind.CORROSION, new Corrosion(inch("2.000"), inch("1.000"), inch("0.006"), true, true));
        held.put(
                DamageKind.LIGHTNING_BURN,
                new LightningBurn(inch("2.000"), inch("2.000"), 9, inch("0.008"), true, false));
        held.put(DamageKind.HOLE, new Opening(inch("0.500"), inch("0.500"), true, true));
        held.put(DamageKind.PUNCTURE, new Opening(inch("0.750"), inch("0.500")));
        held.put(DamageKind.BUCKLE, new Buckle(inch("6.000"), inch("0.120"), deg(90), true));
        held.put(DamageKind.DISBOND, new Disbond(inch("3.000"), inch("2.000"), true, true));
        return Collections.unmodifiableMap(held);
    }

    private static Finding laterRevision() {
        return Sample.card()
                .reMeasured(
                        new Dent(inch("0.040"), inch("3.000"), inch("2.000")),
                        LocalDate.of(2025, 6, 1),
                        Sample.flown(1, 1));
    }

    @Test
    void everyKindOfMeasurementSetRoundTripsUnchanged() {
        EVERY_GEOMETRY.forEach((kind, geometry) -> {
            Map<String, Object> stored = Mapping.measurementsOf(geometry);
            assertThat(Mapping.geometryFrom(kind, stored)).as("%s", kind).isEqualTo(geometry);
        });
    }

    @Test
    void theStoredDocumentHoldsWholeNumbersOnly() {
        for (Geometry geometry : EVERY_GEOMETRY.values()) {
            for (Object value : Mapping.measurementsOf(geometry).values()) {
                assertThat(value).isInstanceOfAny(Integer.class, Boolean.class);
            }
        }
    }

    @Test
    void aStoredDocumentMissingANumberIsRefused() {
        assertThatThrownBy(() -> Mapping.geometryFrom(DamageKind.DENT, Map.of("unexpected", 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a stored dent is missing its depth");
    }

    @Test
    void aStoredLengthThatIsNotANumberIsRefused() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("depth", "deep");
        stored.put("along", 3000);
        stored.put("around", 2000);
        stored.put("sharp_bottom", 0);
        stored.put("cracked", 0);
        assertThatThrownBy(() -> Mapping.geometryFrom(DamageKind.DENT, stored))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a stored length");
    }

    @Test
    void aTailRoundTripsWithItsBulletins() {
        Aircraft before = Sample.aircraft().withEmbodied(new Bulletin(53, 18));
        assertThat(Mapping.rowToAircraft(Mapping.aircraftToRow(before))).isEqualTo(before);
    }

    @Test
    void aWithdrawnTailRoundTripsWithTheDayItWent() {
        Aircraft before = Sample.aircraft().withdrawnOn(LocalDate.of(2026, 6, 30));
        assertThat(Mapping.rowToAircraft(Mapping.aircraftToRow(before))).isEqualTo(before);
    }

    @Test
    void aLogRoundTripsWithEveryPostingOnIt() {
        assertThat(Mapping.rowsToLog(new Serial("5127"), Mapping.logToRows(Sample.flyingLog())))
                .isEqualTo(Sample.flyingLog());
    }

    @Test
    void aLogComesBackInOrderHoweverTheRowsArrive() {
        List<ReadingRow> rows = new ArrayList<>(Mapping.logToRows(Sample.flyingLog()));
        Collections.reverse(rows);
        assertThat(Mapping.rowsToLog(new Serial("5127"), rows)).isEqualTo(Sample.flyingLog());
    }

    @Test
    void aBarrelCardRoundTripsUnchanged() {
        Finding before = Sample.card();
        assertThat(Mapping.rowToFinding(Mapping.findingToRow(before))).isEqualTo(before);
    }

    @Test
    void aWingCardRoundTripsUnchanged() {
        Finding before = Sample.card(Sample.lowerWingPoint());
        assertThat(Mapping.rowToFinding(Mapping.findingToRow(before))).isEqualTo(before);
    }

    @Test
    void aCardAtALaterRevisionRoundTripsWithItsRevision() {
        Finding before = laterRevision();
        Finding after = Mapping.rowToFinding(Mapping.findingToRow(before));
        assertThat(after.revision()).isEqualTo(2);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void aSupersededCardRoundTripsWithWhatReplacedIt() {
        Finding before = Sample.card().superseded(new FindingId(2025, 4200));
        assertThat(Mapping.rowToFinding(Mapping.findingToRow(before)).supersededBy())
                .contains(new FindingId(2025, 4200));
    }

    @Test
    void everyKindOfCardRoundTrips() {
        Map<DamageKind, DamageOrigin> origins = Map.of(
                DamageKind.CORROSION, DamageOrigin.ENVIRONMENT,
                DamageKind.LIGHTNING_BURN, DamageOrigin.LIGHTNING,
                DamageKind.CRACK, DamageOrigin.FATIGUE,
                DamageKind.DISBOND, DamageOrigin.ENVIRONMENT);
        EVERY_GEOMETRY.forEach((kind, geometry) -> {
            Finding before = Sample.card(1, kind, geometry, origins.getOrDefault(kind, DamageOrigin.IMPACT));
            assertThat(Mapping.rowToFinding(Mapping.findingToRow(before)))
                    .as("%s", kind)
                    .isEqualTo(before);
        });
    }

    @Test
    void theRowSaysWhichStructureTheLocationBelongsTo() {
        assertThat(Mapping.findingToRow(Sample.card()).getStructure()).isEqualTo("fuselage");
        assertThat(Mapping.findingToRow(Sample.card(Sample.lowerWingPoint())).getStructure())
                .isEqualTo("wing");
    }

    @Test
    void aRowWithAStructureNobodyRecognisesIsRefused() {
        FindingRow row = Mapping.findingToRow(Sample.card());
        row.setStructure("tailplane");
        assertThatThrownBy(() -> Mapping.rowToFinding(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a structure on file");
    }

    @Test
    void aDispositionRoundTripsWithItsWholeHistory() {
        DispositionRecord before = DispositionRecord.raised(DR)
                .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "structures")
                .movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 4, 1), "structures", "parts");
        assertThat(Mapping.rowToDisposition(Mapping.dispositionToRow(before))).isEqualTo(before);
    }

    @Test
    void aDispositionWithNoHistoryRoundTrips() {
        DispositionRecord before = DispositionRecord.raised(DR);
        assertThat(Mapping.rowToDisposition(Mapping.dispositionToRow(before))).isEqualTo(before);
    }

    @Test
    void theMovesComeBackInTheOrderTheyHappened() {
        DispositionRecord before = DispositionRecord.raised(DR)
                .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "structures")
                .movedTo(DispositionState.IN_WORK, LocalDate.of(2025, 4, 1), "hangar");
        DispositionRow row = Mapping.dispositionToRow(before);
        List<MoveRow> reversed = new ArrayList<>(row.getMoves());
        Collections.reverse(reversed);
        row.setMoves(reversed);
        assertThat(row.getMoves().get(0).getSequence()).isEqualTo(2);
        assertThat(Mapping.rowToDisposition(row)).isEqualTo(before);
    }
}
