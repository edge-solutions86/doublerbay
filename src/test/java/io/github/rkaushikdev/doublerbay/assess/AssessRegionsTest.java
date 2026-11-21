package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssessRegionsTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Crack crack(String length) {
        return new Crack(inch(length), new Angle(0));
    }

    private static Crack crack() {
        return crack("0.100");
    }

    private static Dent dent(String depth) {
        return new Dent(inch(depth), inch("3.000"), inch("2.000"));
    }

    private static Finding card(Geometry geometry, DamageKind kind, DamageOrigin origin) {
        return Sample.cardWith()
                .sequence(1)
                .kind(kind)
                .geometry(geometry != null ? geometry : dent("0.020"))
                .at(Sample.cabinPoint())
                .origin(origin)
                .build();
    }

    private static Case caseOf(Geometry geometry, Region region, DamageKind kind, DamageOrigin origin) {
        Finding finding = card(geometry, kind, origin);
        Placement placement = Placement.place(Sample.tailContext(), finding).withRegion(region);
        Book book = Book.v320();
        LimitRow row = book.limitsFor(
                placement.chapter(),
                Sample.DAY,
                Sample.aircraft(),
                finding.kind(),
                placement.panelKind(),
                placement.region());
        return new Case(finding, placement, row.limits(), book.cleanup().forKind(finding.kind()));
    }

    private static Case caseOf(Geometry geometry, Region region) {
        return caseOf(geometry, region, DamageKind.DENT, DamageOrigin.IMPACT);
    }

    private static Case caseOf(Region region) {
        return caseOf(null, region);
    }

    private static Case caseOf() {
        return caseOf(Region.OPEN_FIELD);
    }

    private static RegionCase held(Geometry geometry, Region region, DamageKind kind, DamageOrigin origin) {
        return new RegionCase(caseOf(geometry, region, kind, origin));
    }

    private static RegionCase held(Geometry geometry, Region region) {
        return new RegionCase(caseOf(geometry, region));
    }

    private static RegionCase held(Region region) {
        return new RegionCase(caseOf(region));
    }

    private static RegionCase held() {
        return new RegionCase(caseOf());
    }

    private static RegionCase crackAt(Crack geometry, Region region) {
        return held(geometry, region, DamageKind.CRACK, DamageOrigin.FATIGUE);
    }

    @Test
    void aRegionCaseReadsBackWithItsRegion() {
        assertThat(held().toString()).contains("in open field");
    }

    @Test
    void theThinSideIsAShareOfTheNominal() {
        RegionCase made = held();
        BigDecimal share =
                Decimals.times(BigDecimal.valueOf(made.subject().thickness().mils()), Regions.STEP_SHARE);
        assertThat(made.thinSide().mils()).isEqualTo(share.intValue());
    }

    @Test
    void theStepRuleSaysNothingInOpenField() {
        assertThat(Regions.stepResidualCheck(held())).isEmpty();
    }

    @Test
    void theStepRuleAppliesOnAChemMillStep() {
        assertThat(Regions.stepResidualCheck(held(Region.CHEM_MILL_STEP))).isPresent();
    }

    @Test
    void theStepRuleAppliesOnALand() {
        assertThat(Regions.stepResidualCheck(held(Region.LAND))).isPresent();
    }

    @Test
    void theStepRegionsAreTheOnesWhereThicknessChanges() {
        assertThat(Regions.STEP_REGIONS).isEqualTo(Set.of(Region.CHEM_MILL_STEP, Region.LAND));
    }

    @Test
    void aShallowBlendLeavesMaterialOnTheThinSide() {
        Check answer = Regions.stepResidualCheck(held(dent("0.005"), Region.CHEM_MILL_STEP))
                .orElseThrow();
        assertThat(answer.passed()).isTrue();
        assertThat(answer.toString()).contains("left of");
    }

    @Test
    void aDeepBlendGoesThroughTheThinSide() {
        Check answer = Regions.stepResidualCheck(held(dent("0.400"), Region.CHEM_MILL_STEP))
                .orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.toString()).contains("goes through");
        assertThat(answer.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void theCornerRuleSaysNothingInOpenField() {
        assertThat(Regions.cornerCrackCheck(held(crack(), Region.OPEN_FIELD, DamageKind.CRACK, DamageOrigin.IMPACT)))
                .isEmpty();
    }

    @Test
    void theCornerRuleSaysNothingAboutADent() {
        assertThat(Regions.cornerCrackCheck(held(Region.CUTOUT_CORNER))).isEmpty();
    }

    @Test
    void theCornerRegionsAreTheCutoutOnes() {
        assertThat(Regions.CORNER_REGIONS).isEqualTo(Set.of(Region.CUTOUT_CORNER, Region.CUTOUT_EDGE));
    }

    @Test
    void aShortCrackAtACornerPasses() {
        Check answer = Regions.cornerCrackCheck(crackAt(crack("0.100"), Region.CUTOUT_CORNER))
                .orElseThrow();
        assertThat(answer.passed()).isTrue();
        assertThat(answer.toString()).contains("at the corner");
    }

    @Test
    void aLongCrackAtACornerGoesOutOfLimits() {
        Check answer = Regions.cornerCrackCheck(crackAt(crack("0.500"), Region.CUTOUT_CORNER))
                .orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.outcome()).isSameAs(Disposition.OUT_OF_LIMITS);
    }

    @Test
    void aCrackExactlyAtTheThresholdIsOutOfLimits() {
        Check answer = Regions.cornerCrackCheck(
                        crackAt(new Crack(Regions.CORNER_CRACK, new Angle(0)), Region.CUTOUT_CORNER))
                .orElseThrow();
        assertThat(answer.passed()).isFalse();
    }

    @Test
    void aCrackInTheKeepOutIsNamedDifferently() {
        Check answer = Regions.cornerCrackCheck(crackAt(crack("0.100"), Region.CUTOUT_EDGE))
                .orElseThrow();
        assertThat(answer.toString()).contains("in the keep out");
    }

    @Test
    void theDoublerRuleSaysNothingInOpenField() {
        assertThat(Regions.underDoublerCheck(held())).isEmpty();
    }

    @Test
    void aDamageUnderADoublerAlwaysFails() {
        Check answer = Regions.underDoublerCheck(held(Region.UNDER_DOUBLER)).orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.toString()).contains("comes off before");
    }

    @Test
    void openFieldCollectsNoRegionRules() {
        assertThat(Regions.checksFor(caseOf())).isEmpty();
    }

    @Test
    void aChemMillStepCollectsTheStepRule() {
        assertThat(Regions.checksFor(caseOf(Region.CHEM_MILL_STEP))).hasSize(1);
    }

    @Test
    void underADoublerCollectsTheDoublerRule() {
        assertThat(Regions.checksFor(caseOf(Region.UNDER_DOUBLER))).hasSize(1);
    }

    @Test
    void aCrackAtACornerCollectsTheCornerRule() {
        Case found = caseOf(crack("0.500"), Region.CUTOUT_CORNER, DamageKind.CRACK, DamageOrigin.FATIGUE);
        assertThat(Regions.checksFor(found)).hasSize(1);
    }
}
