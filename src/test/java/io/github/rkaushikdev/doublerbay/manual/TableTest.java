package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TableTest {

    private static final Reference PAGE = Reference.parse("53-10-03");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitRow row(DamageKind damage, PanelKind panel, Region region, String depth) {
        return new LimitRow(
                LimitKey.of(damage, panel, region), LimitSet.allowable().withMaxDepth(inch(depth)), PAGE);
    }

    private static LimitRow row(PanelKind panel, Region region, String depth) {
        return row(DamageKind.DENT, panel, region, depth);
    }

    private static LimitRow row(PanelKind panel, String depth) {
        return row(panel, null, depth);
    }

    private static LimitRow row(Region region, String depth) {
        return row(null, region, depth);
    }

    private static LimitRow row(String depth) {
        return row(null, null, depth);
    }

    private static LimitRow row(PanelKind panel) {
        return row(panel, "0.010");
    }

    private static LimitRow row(DamageKind damage) {
        return row(damage, null, null, "0.010");
    }

    private static LimitRow row() {
        return row("0.010");
    }

    private static LimitTable table(LimitRow... rows) {
        if (rows.length == 0) {
            rows = new LimitRow[] {
                row("0.010"),
                row(PanelKind.PRESSURISED_SKIN, "0.008"),
                row(PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW, "0.004"),
            };
        }
        return new LimitTable(PAGE, 12, List.of(rows));
    }

    private static Length depthOf(LimitRow found) {
        return found.limits().maxDepth().orElseThrow();
    }

    @Test
    void aKeyReadsBackWhatItIsKeyedOn() {
        assertThat(new LimitKey(DamageKind.DENT)).hasToString("dent on any skin anywhere");
        LimitKey keyed = new LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW);
        assertThat(keyed).hasToString("dent on pressurised skin on a fastener row");
    }

    @Test
    void specificityRanksARegionRowAboveAPanelRow() {
        assertThat(new LimitKey(DamageKind.DENT).specificity()).isEqualTo(1);
        assertThat(new LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN).specificity())
                .isEqualTo(3);
        assertThat(LimitKey.of(DamageKind.DENT, null, Region.LAND).specificity())
                .isEqualTo(5);
        assertThat(new LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND).specificity())
                .isEqualTo(7);
    }

    @Test
    void aRowKeyedOnTheRegionAnswersOverOneKeyedOnTheSkin() {
        LimitTable both = table(row(PanelKind.PRESSURISED_SKIN, "0.008"), row(Region.FASTENER_ROW, "0.004"));
        LimitRow found = both.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW);
        assertThat(depthOf(found)).isEqualTo(inch("0.004"));
    }

    @Test
    void aBlankFieldMatchesAnythingInThatField() {
        LimitKey openKey = new LimitKey(DamageKind.DENT);
        assertThat(openKey.matches(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND))
                .isTrue();
        assertThat(openKey.matches(DamageKind.DENT, PanelKind.WING_LOWER, Region.OPEN_FIELD))
                .isTrue();
        assertThat(openKey.matches(DamageKind.CRACK, PanelKind.PRESSURISED_SKIN, Region.LAND))
                .isFalse();
    }

    @Test
    void aPinnedFieldOnlyMatchesItself() {
        LimitKey keyed = new LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN);
        assertThat(keyed.matches(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND))
                .isTrue();
        assertThat(keyed.matches(DamageKind.DENT, PanelKind.BELLY_SKIN, Region.LAND))
                .isFalse();
    }

    @Test
    void aTableReadsBackItsPageAndSize() {
        assertThat(table()).hasToString("53-10-03 rev 12, 3 rows");
        assertThat(table().size()).isEqualTo(3);
    }

    @Test
    void aTableNeedsRowsAndARevision() {
        assertThatThrownBy(() -> new LimitTable(PAGE, 12, List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has rows in it");
        assertThatThrownBy(() -> new LimitTable(PAGE, 0, List.of(row())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is issued at a revision");
    }

    @Test
    void theMostSpecificMatchingRowAnswers() {
        LimitRow found = table().lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW);
        assertThat(depthOf(found)).isEqualTo(inch("0.004"));
    }

    @Test
    void aLessSpecificRowAnswersWhenNothingTighterMatches() {
        LimitRow found = table().lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD);
        assertThat(depthOf(found)).isEqualTo(inch("0.008"));
    }

    @Test
    void theCatchAllRowAnswersWhenNothingElseDoes() {
        LimitRow found = table().lookup(DamageKind.DENT, PanelKind.WING_LOWER, Region.OPEN_FIELD);
        assertThat(depthOf(found)).isEqualTo(inch("0.010"));
    }

    @Test
    void theCandidatesComeBackMostSpecificFirst() {
        List<LimitRow> found = table().candidates(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW);
        assertThat(found.stream().map(one -> one.key().specificity()).toList()).containsExactly(7, 3, 1);
    }

    @Test
    void aDamageKindWithNoRowIsALookupMiss() {
        assertThatThrownBy(() -> table().lookup(DamageKind.CRACK, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no row for crack");
    }

    @Test
    void twoRowsThatAnswerEquallyAreAContradictionAndNotATie() {
        LimitTable contradictory =
                table(row("0.010"), row(PanelKind.PRESSURISED_SKIN, "0.008"), row(PanelKind.PRESSURISED_SKIN, "0.004"));
        assertThatThrownBy(() -> contradictory.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has 2 rows that answer");
    }

    @Test
    void aContradictionOnlyBitesWhereBothRowsActuallyMatch() {
        LimitTable contradictory =
                table(row("0.010"), row(PanelKind.PRESSURISED_SKIN, "0.008"), row(PanelKind.PRESSURISED_SKIN, "0.004"));
        LimitRow found = contradictory.lookup(DamageKind.DENT, PanelKind.WING_LOWER, Region.OPEN_FIELD);
        assertThat(depthOf(found)).isEqualTo(inch("0.010"));
    }

    @Test
    void aTableSaysWhetherItCouldAnswerAtAll() {
        assertThat(table().hasARowFor(DamageKind.DENT, PanelKind.WING_LOWER, Region.LAND))
                .isTrue();
        assertThat(table().hasARowFor(DamageKind.CRACK, PanelKind.WING_LOWER, Region.LAND))
                .isFalse();
    }

    @Test
    void aTableListsTheKindsItCovers() {
        LimitTable covering = table(row(DamageKind.DENT), row(DamageKind.SCRATCH));
        assertThat(covering.kindsCovered()).isEqualTo(Set.of(DamageKind.DENT, DamageKind.SCRATCH));
    }

    @Test
    void addingARowLeavesTheEarlierTableAlone() {
        LimitTable before = table();
        LimitTable after = before.withRow(row(DamageKind.SCRATCH));
        assertThat(after.size()).isEqualTo(4);
        assertThat(before.size()).isEqualTo(3);
    }

    @Test
    void aRowReadsBackItsPageKeyAndLimits() {
        String printed = row(PanelKind.PRESSURISED_SKIN).toString();
        assertThat(printed).startsWith("53-10-03 dent on pressurised skin anywhere");
        assertThat(printed).contains("depth 0.010");
    }
}
