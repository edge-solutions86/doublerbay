package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aCalloutReadsBackTheWayTheDrawingWritesIt() {
        assertThat(Material.lookup("2024-T3 CLAD")).hasToString("2024-T3 CLAD");
        assertThat(Material.lookup("7075-T6")).hasToString("7075-T6");
    }

    @Test
    void aCalloutIsLookedUpCaseInsensitively() {
        assertThat(Material.lookup(" 2024-t3 clad ")).isEqualTo(Material.lookup("2024-T3 CLAD"));
        assertThat(Material.isCarried("2024-t3 clad")).isTrue();
        assertThat(Material.isCarried("6061-T6")).isFalse();
    }

    @Test
    void aMaterialOffTheCatalogueIsALookupMiss() {
        assertThatThrownBy(() -> Material.lookup("6061-T6"))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not a material this desk carries");
    }

    @Test
    void theCatalogueKeysAreTheCallouts() {
        for (Map.Entry<String, Material> entry : Material.CATALOGUE.entrySet()) {
            assertThat(entry.getValue()).hasToString(entry.getKey());
        }
    }

    @Test
    void theTwoThousandSeriesIsTheDamageTolerantOne() {
        assertThat(Material.lookup("2024-T3 CLAD").isDamageTolerant()).isTrue();
        assertThat(Material.lookup("7075-T6").isDamageTolerant()).isFalse();
        assertThat(Family.TWO_THOUSAND.isDamageTolerant()).isTrue();
        assertThat(Family.SEVEN_THOUSAND).hasToString("7xxx");
    }

    @Test
    void aSeriesTheDeskDoesNotCarryIsRefusedWhenAsked() {
        Material odd = new Material("6061", Temper.T6, false, 45, 40);
        assertThatThrownBy(odd::family)
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a series this desk carries");
    }

    @Test
    void anAlloyDesignationIsFourDigits() {
        assertThatThrownBy(() -> new Material("20244", Temper.T3, true, 64, 47))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not an alloy designation");
        assertThatThrownBy(() -> new Material("T3", Temper.T3, true, 64, 47))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not an alloy designation");
    }

    @Test
    void aMaterialCannotYieldAboveItsUltimate() {
        assertThatThrownBy(() -> new Material("2024", Temper.T3, true, 47, 64))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("yields above its ultimate");
    }

    @Test
    void bareSheetCarriesNoCladding() {
        Material bare = Material.lookup("7075-T6");
        assertThat(bare.cladThickness(inch("0.040"))).isEqualTo(new Length(0));
        assertThat(bare.bareBelow(inch("0.040"))).isEqualTo(inch("0.040"));
        assertThat(bare.isThroughClad(inch("0.040"), new Length(1))).isTrue();
    }

    @Test
    void thinCladSheetIsFivePercentASide() {
        Material clad = Material.lookup("2024-T3 CLAD");
        assertThat(clad.cladThickness(inch("0.040"))).isEqualTo(new Length(2));
        assertThat(clad.bareBelow(inch("0.040"))).isEqualTo(new Length(36));
    }

    @Test
    void heavyCladSheetStepsDownToTwoAndAHalfPercent() {
        Material clad = Material.lookup("2024-T3 CLAD");
        assertThat(clad.cladThickness(inch("0.100"))).isEqualTo(new Length(2));
        assertThat(clad.cladThickness(inch("0.062"))).isEqualTo(new Length(3));
    }

    @Test
    void theStepBelongsToTheThinSide() {
        Material clad = Material.lookup("2024-T3 CLAD");
        Length onTheStep = clad.cladThickness(inch("0.062"));
        Length justOver = clad.cladThickness(inch("0.063"));
        assertThat(onTheStep).isEqualTo(new Length(3));
        assertThat(justOver).isEqualTo(new Length(2));
    }

    @Test
    void aBlendInsideTheCladdingHasNotReachedTheAlloy() {
        Material clad = Material.lookup("2024-T3 CLAD");
        assertThat(clad.isThroughClad(inch("0.040"), new Length(2))).isFalse();
        assertThat(clad.isThroughClad(inch("0.040"), new Length(3))).isTrue();
    }

    @Test
    void theAnnealedTemperNamesItself() {
        assertThat(Temper.ANNEALED.isAnnealed()).isTrue();
        assertThat(Temper.T3.isAnnealed()).isFalse();
        assertThat(Temper.ANNEALED).hasToString("O");
        assertThat(Material.lookup("2024-O CLAD")).hasToString("2024-O CLAD");
    }

    @Test
    void aMaterialIsFrozenAndHashesByValue() {
        assertThat(new HashSet<>(List.of(Material.lookup("2024-T3 CLAD"), Material.lookup("2024-T3 CLAD"))))
                .hasSize(1);
        // Reassigning the cladding does not compile: every field is final and there is no setter.
        Types.isFrozen(Material.class);
    }
}
