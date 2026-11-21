package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeometryTest {

    private static final Offset CENTRE = new Offset(Length.ofInches("100.000"), Length.ofInches("50.000"));

    private static final Map<Geometry, Length> WITH_A_DEPTH = withADepth();

    private static final List<Geometry> WITHOUT_A_DEPTH = List.of(
            new Crack(inch("0.750"), deg(0)),
            new Opening(inch("0.500"), inch("0.500")),
            new Buckle(inch("6.000"), inch("0.120"), deg(90)),
            new Disbond(inch("3.000"), inch("2.000")));

    private static final List<Geometry> EVERY_KIND = everyKind();

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Angle deg(int value) {
        return Angle.ofDegrees(value);
    }

    private static Map<Geometry, Length> withADepth() {
        Map<Geometry, Length> held = new LinkedHashMap<>();
        held.put(new Dent(inch("0.030"), inch("3.000"), inch("2.000")), inch("0.030"));
        held.put(new Scoring(inch("0.010"), inch("2.000"), inch("0.060"), deg(0)), inch("0.010"));
        held.put(new Corrosion(inch("4.000"), inch("3.000"), inch("0.006")), inch("0.006"));
        held.put(new LightningBurn(inch("2.000"), inch("2.000"), 4, inch("0.008")), inch("0.008"));
        return held;
    }

    private static List<Geometry> everyKind() {
        List<Geometry> every = new ArrayList<>(WITH_A_DEPTH.keySet());
        every.addAll(WITHOUT_A_DEPTH);
        return List.copyOf(every);
    }

    @Test
    void everyMeasurementSetAnswersAsAMeasurement() {
        assertThat(EVERY_KIND).hasSize(8);
        for (Geometry geometry : EVERY_KIND) {
            assertThat(geometry).isInstanceOf(Measured.class);
        }
    }

    @Test
    void everyMeasurementSetCoversSomeSkin() {
        for (Geometry geometry : EVERY_KIND) {
            Footprint covered = geometry.footprint(CENTRE);
            assertThat(covered.along().mils()).isPositive();
            assertThat(covered.around().mils()).isPositive();
            assertThat(covered.contains(CENTRE)).isTrue();
        }
    }

    @Test
    void theKindsWithADepthAnswerWithTheOneTheTablesRead() {
        WITH_A_DEPTH.forEach((geometry, expected) -> {
            assertThat(geometry.limitDepth()).contains(expected);
            assertThat(Geometry.hasADepth(geometry)).isTrue();
        });
    }

    @Test
    void theKindsWithoutADepthAnswerWithNothing() {
        for (Geometry geometry : WITHOUT_A_DEPTH) {
            assertThat(geometry.limitDepth()).isEmpty();
            assertThat(Geometry.hasADepth(geometry)).isFalse();
        }
    }

    @Test
    void corrosionAnswersWithItsWorstPitAndNotAnAverage() {
        Corrosion pitted = new Corrosion(inch("4.000"), inch("3.000"), inch("0.012"), true, false);
        assertThat(pitted.limitDepth()).contains(inch("0.012"));
    }

    @Test
    void aLightningBurnAnswersWithItsDeepestPit() {
        LightningBurn burn = new LightningBurn(inch("2.000"), inch("2.000"), 9, inch("0.014"));
        assertThat(burn.limitDepth()).contains(inch("0.014"));
    }

    @Test
    void theClosedSetCoversEveryKindTheDeskMeasures() {
        for (Geometry geometry : EVERY_KIND) {
            assertThat(geometry)
                    .isInstanceOfAny(
                            Dent.class,
                            Scoring.class,
                            Crack.class,
                            Corrosion.class,
                            LightningBurn.class,
                            Opening.class,
                            Buckle.class,
                            Disbond.class);
        }
        assertThat(Geometry.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        Dent.class,
                        Scoring.class,
                        Crack.class,
                        Corrosion.class,
                        LightningBurn.class,
                        Opening.class,
                        Buckle.class,
                        Disbond.class);
    }
}
