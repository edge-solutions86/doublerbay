package io.github.rkaushikdev.doublerbay.damage;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FindingTest {

    private static final Serial MSN = new Serial("5127");
    private static final FindingId DR = new FindingId(2025, 4182);
    private static final LocalDate FOUND = LocalDate.of(2025, 3, 14);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Dent dent(String depth) {
        return new Dent(inch(depth), inch("3.000"), inch("2.000"));
    }

    private static Dent dent() {
        return dent("0.030");
    }

    private static Crack crack() {
        return new Crack(inch("0.750"), Angle.ofDegrees(0));
    }

    private static Finding finding(
            DamageKind kind, Geometry geometry, DiscoverySource source, DamageOrigin origin, FindingId reference) {
        return new Finding(
                reference,
                MSN,
                kind,
                geometry != null ? geometry : dent(),
                Sample.point(),
                FOUND,
                source,
                origin,
                usage(13900, 24400));
    }

    private static Finding finding(DamageKind kind, Geometry geometry, DamageOrigin origin) {
        return finding(kind, geometry, DiscoverySource.SCHEDULED_CHECK, origin, DR);
    }

    private static Finding finding(DiscoverySource source, DamageOrigin origin) {
        return finding(DamageKind.DENT, null, source, origin, DR);
    }

    private static Finding finding() {
        return finding(DamageKind.DENT, null, DamageOrigin.IMPACT);
    }

    private static Finding cracked() {
        return finding(DamageKind.CRACK, crack(), DamageOrigin.FATIGUE);
    }

    @Test
    void aFindingReadsBackItsNumberTailKindAndPlace() {
        assertThat(finding().toString()).startsWith("DR-2025-004182 MSN 5127 dent at FR41");
    }

    @Test
    void aReMeasuredFindingSaysWhichRevisionItIs() {
        Finding later = finding().reMeasured(dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        assertThat(later.toString()).endsWith("rev 2");
        assertThat(later.revision()).isEqualTo(2);
        assertThat(((Dent) later.geometry()).depth()).isEqualTo(inch("0.040"));
    }

    @Test
    void aReMeasurementLeavesTheEarlierRevisionAlone() {
        Finding first = finding();
        first.reMeasured(dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        assertThat(first.revision()).isEqualTo(1);
        assertThat(((Dent) first.geometry()).depth()).isEqualTo(inch("0.030"));
    }

    @Test
    void aDentCardCarryingCrackMeasurementsIsRefused() {
        assertThatThrownBy(() -> finding(DamageKind.DENT, crack(), DamageOrigin.IMPACT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a dent is reported as dent measurements");
    }

    @Test
    void aCrackCardCarryingDentMeasurementsIsRefused() {
        assertThatThrownBy(() -> finding(DamageKind.CRACK, null, DamageOrigin.FATIGUE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a crack is reported as crack measurements");
    }

    @Test
    void theThreeScoringKindsAllTakeTheSameMeasurements() {
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK)) {
            assertThatThrownBy(() -> finding(kind, null, DamageOrigin.IMPACT))
                    .isInstanceOf(BadReading.class)
                    .hasMessageContaining("scoring measurements");
        }
    }

    @Test
    void aKindAndACauseThatDoNotGoTogetherAreRefused() {
        assertThatThrownBy(() -> finding(DamageKind.DENT, null, DamageOrigin.LIGHTNING))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a dent does not come from lightning");
    }

    @Test
    void aReportOfSomethingThatHappenedNeedsACauseThatHappened() {
        assertThatThrownBy(() -> finding(DiscoverySource.GROUND_HANDLING, DamageOrigin.UNKNOWN))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("names something that happened");
    }

    @Test
    void aReportOfSomethingThatHappenedTakesAnEventCause() {
        Finding raised = finding(DiscoverySource.GROUND_HANDLING, DamageOrigin.HANDLING);
        assertThat(raised.source()).isSameAs(DiscoverySource.GROUND_HANDLING);
    }

    @Test
    void aFindingStartsAtRevisionOne() {
        assertThatThrownBy(() -> new Finding(
                        DR,
                        MSN,
                        DamageKind.DENT,
                        dent(),
                        Sample.point(),
                        FOUND,
                        DiscoverySource.SCHEDULED_CHECK,
                        DamageOrigin.IMPACT,
                        usage(13900, 24400),
                        0,
                        Optional.empty(),
                        ""))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("starts at revision one");
    }

    @Test
    void aFindingIsOpenUntilSomethingSupersedesIt() {
        assertThat(finding().isOpen()).isTrue();
        Finding closed = finding().superseded(new FindingId(2025, 4200));
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.supersededBy()).contains(new FindingId(2025, 4200));
    }

    @Test
    void aFindingCannotSupersedeItself() {
        assertThatThrownBy(() -> finding().superseded(DR))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cannot supersede itself");
    }

    @Test
    void aCrackFindingKnowsItHasNoAllowableSize() {
        assertThat(cracked().isNeverAllowable()).isTrue();
        assertThat(finding().isNeverAllowable()).isFalse();
    }

    @Test
    void theGoverningDepthComesOffWhateverKindItIs() {
        assertThat(finding().governingDepth()).isEqualTo(inch("0.030"));
    }

    @Test
    void aKindWithNoDepthRefusesToGiveOne() {
        Finding cracked = cracked();
        assertThatThrownBy(cracked::governingDepth)
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not measured with a depth");
    }

    @Test
    void theFootprintComesOffTheGeometry() {
        Offset centre = new Offset(inch("630.000"), inch("20.000"));
        assertThat(finding().footprint(centre).along()).isEqualTo(inch("3.000"));
    }

    @Test
    void aBarrelFindingIsNotAWingFinding() {
        assertThat(finding().isOnTheWing()).isFalse();
    }

    @Test
    void aReMeasurementBeforeTheFindingIsRefused() {
        assertThatThrownBy(() -> finding().reMeasured(dent("0.040"), LocalDate.of(2025, 1, 1), usage(13000, 24000)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before the finding");
    }

    @Test
    void aReMeasurementAsAnotherKindOfGeometryIsRefused() {
        assertThatThrownBy(() -> finding().reMeasured(crack(), LocalDate.of(2025, 6, 1), usage(14200, 24900)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not re measured as something else");
    }
}
