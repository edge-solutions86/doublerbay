package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Clusters;
import io.github.rkaushikdev.doublerbay.damage.SeparationRule;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InteractionTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aSeparationRuleTakesTheLargerExtentAndMultipliesIt() {
        SeparationRule rule = new SeparationRule(new BigDecimal("2"), inch("1.000"));
        assertThat(rule.between(inch("3.000"), inch("1.000"))).isEqualTo(inch("6.000"));
        assertThat(rule.between(inch("1.000"), inch("3.000"))).isEqualTo(inch("6.000"));
    }

    @Test
    void theFloorHoldsWhenBothDamagesAreSmall() {
        SeparationRule rule = new SeparationRule(new BigDecimal("1"), inch("1.000"));
        assertThat(rule.between(inch("0.250"), inch("0.250"))).isEqualTo(inch("1.000"));
    }

    @Test
    void aSeparationRuleReadsBackHowItIsStated() {
        assertThat(Clusters.DEFAULT_SEPARATION).hasToString("1 times the larger, floor 1.000");
    }

    @Test
    void aSeparationMultipleOfNothingIsRefused() {
        assertThatThrownBy(() -> new SeparationRule(new BigDecimal("0"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a multiple");
    }

    @Test
    void aSeparationFloorThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> new SeparationRule(new BigDecimal("1"), inch("-0.001")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void countLimitsReadBackWhatTheyCount() {
        assertThat(new CountLimits(2, 6, 3)).hasToString("3 in a group, 2 a bay, 6 a panel");
        assertThat(new CountLimits()).hasToString("not counted");
    }

    @Test
    void aPageThatCountsNothingSaysSo() {
        assertThat(new CountLimits().countsAnything()).isFalse();
        assertThat(new CountLimits().withPerPanel(6).countsAnything()).isTrue();
    }

    @Test
    void aCountLimitOfNothingIsRefused() {
        assertThatThrownBy(() -> new CountLimits().withPerBay(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("per bay allows at least one");
        assertThatThrownBy(() -> new CountLimits().withPerPanel(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("per panel allows at least one");
    }

    @Test
    void aBayCannotHoldMoreThanThePanelItIsIn() {
        assertThatThrownBy(() -> CountLimits.of(8, 6, null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cannot hold more than the panel");
    }

    @Test
    void aCountAtTheLimitIsNotOverIt() {
        CountLimits counts = new CountLimits(2, 6, 3);
        assertThat(counts.bayExceeded(2)).isFalse();
        assertThat(counts.bayExceeded(3)).isTrue();
        assertThat(counts.panelExceeded(6)).isFalse();
        assertThat(counts.panelExceeded(7)).isTrue();
        assertThat(counts.clusterExceeded(3)).isFalse();
        assertThat(counts.clusterExceeded(4)).isTrue();
    }

    @Test
    void aLimitTheBookDoesNotStateIsNeverExceeded() {
        assertThat(new CountLimits().bayExceeded(99)).isFalse();
        assertThat(new CountLimits().panelExceeded(99)).isFalse();
        assertThat(new CountLimits().clusterExceeded(99)).isFalse();
    }

    @Test
    void theRoomLeftOnAPanelRunsDownAndStopsAtNothing() {
        CountLimits counts = new CountLimits().withPerPanel(6);
        assertThat(counts.roomLeftOnPanel(2)).contains(4);
        assertThat(counts.roomLeftOnPanel(6)).contains(0);
        assertThat(counts.roomLeftOnPanel(9)).contains(0);
    }

    @Test
    void aPanelTheBookDoesNotCountHasNoRoomToReport() {
        assertThat(new CountLimits().withPerBay(2).roomLeftOnPanel(2)).isEmpty();
    }

    @Test
    void theRulesComeOffAPageTheWayItStatesThem() {
        InteractionRules rules = InteractionRules.fromBook("1.5", "2.000", null, 4, null);
        assertThat(rules.separationBetween(inch("4.000"), inch("2.000"))).isEqualTo(inch("6.000"));
        assertThat(rules.counts().perPanel()).contains(4);
        assertThat(rules.counts().perBay()).isEmpty();
    }

    @Test
    void theDefaultRulesCarryTheV320Numbers() {
        assertThat(InteractionRules.DEFAULT_RULES.counts().perBay()).contains(2);
        assertThat(InteractionRules.DEFAULT_RULES.counts().perPanel()).contains(6);
        assertThat(InteractionRules.DEFAULT_RULES.counts().perCluster()).contains(3);
        assertThat(InteractionRules.DEFAULT_RULES.separationBetween(inch("0.100"), inch("0.100")))
                .isEqualTo(inch("1.000"));
        // The same rules written to another number of places are the same rules.
        assertThat(InteractionRules.DEFAULT_RULES).isEqualTo(InteractionRules.fromBook("1.0", "1.000", 2, 6, 3));
    }

    @Test
    void theRulesReadBackAsOneLine() {
        String printed = InteractionRules.DEFAULT_RULES.toString();
        assertThat(printed).startsWith("separation 1 times the larger");
        assertThat(printed).contains("counts 3 in a group, 2 a bay, 6 a panel");
    }

    @Test
    void theCountAfterClusteringIsTheOneThatMatters() {
        CountLimits counts = new CountLimits().withPerPanel(6);
        assertThat(counts.panelExceeded(8)).isTrue();
        assertThat(counts.panelExceeded(5)).isFalse();
    }
}
