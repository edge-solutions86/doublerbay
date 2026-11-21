package io.github.rkaushikdev.doublerbay.fleet;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.v320.Fuselage;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogueTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 1);

    private static Aircraft tail(String serial, String marks, Series series) {
        return new Aircraft(new Serial(serial), new Registration(marks), series, 1140, LocalDate.of(2016, 4, 12));
    }

    private static Aircraft tail() {
        return tail("5127", "N412VD", Series.STANDARD);
    }

    private static Fleet fleet() {
        Fleet register = new Fleet();
        register.enrol(
                tail(),
                new UtilisationLog(
                        new Serial("5127"),
                        List.of(
                                new Snapshot(LocalDate.of(2024, 1, 1), usage(12000, 21000)),
                                new Snapshot(DAY, usage(13800, 24200)))));
        register.enrol(tail("5301", "N155VD", Series.SHORT));
        return register;
    }

    @Test
    void theShortSeriesBarrelIsShorterAndNothingElseChanges() {
        Airframe shorter = Fuselage.barrelFor(53);
        Airframe standard = Fuselage.barrelFor(Fuselage.FRAME_COUNT);
        assertThat(shorter.grid().frames()).hasSize(53);
        assertThat(shorter.panels().size()).isLessThan(standard.panels().size());
        assertThat(shorter.grid().stringers()).isEqualTo(standard.grid().stringers());
    }

    @Test
    void aBarrelWithNoTailConeIsRefused() {
        assertThatThrownBy(() -> Fuselage.barrelFor(20))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has no tail cone");
    }

    @Test
    void aBarrelOfALengthIsBuiltOnceAndShared() {
        assertThat(Fuselage.barrelFor(53)).isSameAs(Fuselage.barrelFor(53));
        assertThat(Fuselage.barrelFor(53)).isNotSameAs(Fuselage.barrelFor(Fuselage.FRAME_COUNT));
        assertThat(Fuselage.barrel()).isSameAs(Fuselage.barrelFor(Fuselage.FRAME_COUNT));
    }

    @Test
    void theSectionsFloatAtTheBackAndNotAtTheFront() {
        assertThat(Fuselage.sectionOf(1, 53)).isEqualTo("forward");
        assertThat(Fuselage.sectionOf(20, 53)).isEqualTo("centre");
        assertThat(Fuselage.sectionOf(45, 53)).isEqualTo("aft");
        assertThat(Fuselage.sectionOf(45, Fuselage.FRAME_COUNT)).isEqualTo("aft");
    }

    @Test
    void theCatalogueCarriesEverySeriesTheFleetFlies() {
        TypeCatalogue catalogue = new TypeCatalogue();
        assertThat(catalogue.size()).isEqualTo(3);
        for (Series series : Series.values()) {
            assertThat(catalogue.carries(series)).isTrue();
        }
    }

    @Test
    void theShortSeriesHasFewerFramesOnFile() {
        TypeCatalogue catalogue = new TypeCatalogue();
        assertThat(catalogue.framesOn(Series.SHORT)).isLessThan(catalogue.framesOn(Series.STANDARD));
        assertThat(catalogue.framesOn(Series.LONG_RANGE)).isEqualTo(catalogue.framesOn(Series.STANDARD));
    }

    @Test
    void aSeriesOffTheCatalogueIsALookupMiss() {
        TypeCatalogue catalogue = new TypeCatalogue(Map.of(Series.STANDARD, Fuselage.FRAME_COUNT));
        assertThat(catalogue.carries(Series.SHORT)).isFalse();
        assertThatThrownBy(() -> catalogue.definitionFor(Series.SHORT))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no structure definition on file");
        assertThatThrownBy(() -> catalogue.framesOn(Series.SHORT))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no structure definition on file");
    }

    @Test
    void aDefinitionCarriesABarrelAndAWing() {
        TypeDefinition definition = new TypeCatalogue().definitionFor(Series.STANDARD);
        assertThat(definition.barrel().panels()).isNotEmpty();
        assertThat(definition.wingBox().panels()).isNotEmpty();
        assertThat(definition.toString()).startsWith("V320-200: V320-200");
    }

    @Test
    void aContextResolvesTheTailTheCountersAndTheStructureTogether() {
        TailContext context = TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("5127"), DAY);
        assertThat(context.serial()).isEqualTo(new Serial("5127"));
        assertThat(context.flown().cycles()).isEqualTo(new Cycles(13800));
        assertThat(context.barrel())
                .isSameAs(new TypeCatalogue().definitionFor(Series.STANDARD).barrel());
        assertThat(context.wingBox().panels()).isNotEmpty();
    }

    @Test
    void aShortSeriesTailResolvesOntoTheShortBarrel() {
        TailContext context = TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("5301"), DAY);
        assertThat(context.barrel().grid().frames()).hasSize(53);
    }

    @Test
    void aContextReadsBackTheTailTheDayAndTheCounters() {
        TailContext context = TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("5127"), DAY);
        assertThat(context).hasToString("N412VD (MSN 5127, V320-200) on 2025-01-01, 13800 FC / 24200.0 FH");
    }

    @Test
    void theAgeComesOffTheTailAndTheDay() {
        TailContext context = TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("5127"), DAY);
        assertThat(context.ageYears()).isEqualByComparingTo(tail().ageYears(DAY));
    }

    @Test
    void effectivityIsAnsweredThroughTheContext() {
        TailContext context = TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("5127"), DAY);
        assertThat(context.coveredBy(Effectivity.allAircraft().withSerials(5000, 5200)))
                .isTrue();
        assertThat(context.coveredBy(Effectivity.allAircraft().withFirstSerial(5300)))
                .isFalse();
    }

    @Test
    void aTailNotInServiceOnTheDayHasNoContext() {
        assertThatThrownBy(() -> TypeCatalogue.contextFor(
                        fleet(), new TypeCatalogue(), new Serial("5127"), LocalDate.of(2010, 1, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("was not in service");
    }

    @Test
    void aTailOffTheRegisterHasNoContext() {
        assertThatThrownBy(() -> TypeCatalogue.contextFor(fleet(), new TypeCatalogue(), new Serial("9999"), DAY))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
    }
}
