package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KindTest {

    @Test
    void aKindNamesItselfTheWayTheFormDoes() {
        assertThat(DamageKind.LIGHTNING_BURN).hasToString("lightning burn");
        assertThat(DamageOrigin.HANDLING).hasToString("ground handling");
        assertThat(DamageKind.fromValue("lightning burn")).isSameAs(DamageKind.LIGHTNING_BURN);
        assertThat(DamageOrigin.fromValue("ground handling")).isSameAs(DamageOrigin.HANDLING);
    }

    @Test
    void deformationMovesMetalAndMaterialLossRemovesIt() {
        assertThat(DamageKind.DENT.isDeformation()).isTrue();
        assertThat(DamageKind.BUCKLE.isDeformation()).isTrue();
        assertThat(DamageKind.DENT.isMaterialLoss()).isFalse();
        assertThat(DamageKind.GOUGE.isMaterialLoss()).isTrue();
        assertThat(DamageKind.GOUGE.isDeformation()).isFalse();
    }

    @Test
    void aCrackIsNeitherDeformationNorMaterialLoss() {
        assertThat(DamageKind.CRACK.isDeformation()).isFalse();
        assertThat(DamageKind.CRACK.isMaterialLoss()).isFalse();
    }

    @Test
    void throughThicknessDamageHasNothingLeftToBlend() {
        assertThat(DamageKind.HOLE.isThroughThickness()).isTrue();
        assertThat(DamageKind.PUNCTURE.isThroughThickness()).isTrue();
        assertThat(DamageKind.GOUGE.isThroughThickness()).isFalse();
        assertThat(DamageKind.CRACK.isThroughThickness()).isFalse();
    }

    @Test
    void theKindsWithNoAllowableLimitAtAnySize() {
        Set<DamageKind> never = Arrays.stream(DamageKind.values())
                .filter(DamageKind::isNeverAllowable)
                .collect(Collectors.toSet());
        assertThat(never).isEqualTo(Set.of(DamageKind.CRACK, DamageKind.HOLE, DamageKind.PUNCTURE, DamageKind.BUCKLE));
    }

    @Test
    void theKindsThatLieAlongADirectionNeedABearing() {
        assertThat(DamageKind.CRACK.hasABearing()).isTrue();
        assertThat(DamageKind.SCRATCH.hasABearing()).isTrue();
        assertThat(DamageKind.DENT.hasABearing()).isFalse();
        assertThat(DamageKind.CORROSION.hasABearing()).isFalse();
    }

    @Test
    void aDepthIsAskedForWhereverMetalMovedOrWent() {
        assertThat(DamageKind.DENT.needsDepth()).isTrue();
        assertThat(DamageKind.CORROSION.needsDepth()).isTrue();
        assertThat(DamageKind.CRACK.needsDepth()).isFalse();
        assertThat(DamageKind.DISBOND.needsDepth()).isFalse();
    }

    @Test
    void aPatchOfDamageIsMeasuredAsAnArea() {
        assertThat(DamageKind.CORROSION.needsArea()).isTrue();
        assertThat(DamageKind.DISBOND.needsArea()).isTrue();
        assertThat(DamageKind.LIGHTNING_BURN.needsArea()).isTrue();
        assertThat(DamageKind.DENT.needsArea()).isFalse();
    }

    @Test
    void everyKindIsFiledUnderASection() {
        for (DamageKind kind : DamageKind.values()) {
            assertThat(kind.section()).isIn(10, 20, 30, 40, 50);
        }
    }

    @Test
    void deformationAndCrackingAreFiledApart() {
        assertThat(DamageKind.DENT.section()).isNotEqualTo(DamageKind.CRACK.section());
        assertThat(DamageKind.DENT.section()).isEqualTo(DamageKind.BUCKLE.section());
        assertThat(DamageKind.SCRATCH.section()).isEqualTo(DamageKind.GOUGE.section());
    }

    @Test
    void anOriginThatHappenedAtAMomentCanBeLookedUpInTheLog() {
        assertThat(DamageOrigin.IMPACT.isAnEvent()).isTrue();
        assertThat(DamageOrigin.LIGHTNING.isAnEvent()).isTrue();
        assertThat(DamageOrigin.FATIGUE.isAnEvent()).isFalse();
    }

    @Test
    void aProgressiveOriginGetsWorseBetweenInspections() {
        assertThat(DamageOrigin.FATIGUE.isProgressive()).isTrue();
        assertThat(DamageOrigin.ENVIRONMENT.isProgressive()).isTrue();
        assertThat(DamageOrigin.IMPACT.isProgressive()).isFalse();
    }

    @Test
    void everyKindAdmitsAnUnknownCause() {
        for (DamageKind kind : DamageKind.values()) {
            assertThat(DamageKind.plausibleOrigins(kind)).contains(DamageOrigin.UNKNOWN);
        }
    }

    @Test
    void corrosionComesFromTheEnvironmentAndNotFromABeltLoader() {
        assertThat(DamageKind.isPlausible(DamageKind.CORROSION, DamageOrigin.ENVIRONMENT))
                .isTrue();
        assertThat(DamageKind.isPlausible(DamageKind.CORROSION, DamageOrigin.HANDLING))
                .isFalse();
    }

    @Test
    void aCrackCanBeFatigueOrImpactButNotWeather() {
        assertThat(DamageKind.isPlausible(DamageKind.CRACK, DamageOrigin.FATIGUE))
                .isTrue();
        assertThat(DamageKind.isPlausible(DamageKind.CRACK, DamageOrigin.IMPACT))
                .isTrue();
        assertThat(DamageKind.isPlausible(DamageKind.CRACK, DamageOrigin.ENVIRONMENT))
                .isFalse();
    }

    @Test
    void aLightningBurnComesFromLightning() {
        assertThat(DamageKind.plausibleOrigins(DamageKind.LIGHTNING_BURN))
                .isEqualTo(Set.of(DamageOrigin.LIGHTNING, DamageOrigin.UNKNOWN));
    }

    @Test
    void aPairingThatDoesNotGoTogetherIsRefused() {
        assertThatCode(() -> DamageKind.checkPairing(DamageKind.DENT, DamageOrigin.IMPACT))
                .doesNotThrowAnyException();
        assertThatCode(() -> DamageKind.checkPairing(DamageKind.DENT, DamageOrigin.UNKNOWN))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DamageKind.checkPairing(DamageKind.DENT, DamageOrigin.LIGHTNING))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a dent does not come from lightning");
    }
}
