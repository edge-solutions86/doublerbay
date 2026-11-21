package io.github.rkaushikdev.doublerbay.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.WrongMeasure;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdsTest {

    @Test
    void everyRefusalIsCaughtByOneCatchOfThePackageError() {
        for (Class<?> kind :
                List.of(BadReading.class, WrongMeasure.class, NotOnFile.class, Refused.class, OutOfSequence.class)) {
            assertThat(DoublerbayError.class.isAssignableFrom(kind))
                    .as(kind.getSimpleName())
                    .isTrue();
        }
        assertThat(RuntimeException.class.isAssignableFrom(DoublerbayError.class))
                .isTrue();
    }

    @Test
    void aRefusalCarriesItsReasonAndDetail() {
        Refused refusal = new Refused("no limit on file", "53-10-03 at revision 12");
        assertThat(refusal.reason()).isEqualTo("no limit on file");
        assertThat(refusal.detail()).contains("53-10-03 at revision 12");
        assertThat(refusal.getMessage()).isEqualTo("no limit on file: 53-10-03 at revision 12");
    }

    @Test
    void aRefusalWithoutDetailReadsAsItsReason() {
        assertThat(new Refused("out of limits").getMessage()).isEqualTo("out of limits");
        assertThat(new Refused("out of limits").detail()).isEmpty();
    }

    @Test
    void theMeasureModulesRefuseThroughThePackageErrors() {
        assertThatThrownBy(() -> Length.ofInches("0.0405")).isInstanceOf(BadReading.class);
        // A length built from a fraction of a mil does not compile: the constructor takes an int.
        Types.constructorsTakeOnly(Length.class, int.class);
    }

    @Test
    void oneCatchOfThePackageErrorStillCatchesEveryRefusal() {
        assertThatThrownBy(() -> Length.ofInches("0.0405")).isInstanceOf(DoublerbayError.class);
        assertThatThrownBy(() -> new Serial("51")).isInstanceOf(DoublerbayError.class);
        Types.constructorsTakeOnly(Length.class, int.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N412VD", "N1", "N99999", "N412V"})
    void aRegistrationTakesTheShapesTheRegisterIssues(String marks) {
        assertThat(new Registration(marks)).hasToString(marks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "412VD", "N0412", "NVD", "N412VDX", "n412vd "})
    void aRegistrationRefusesAnythingElse(String marks) {
        assertThatThrownBy(() -> new Registration(marks))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a registration");
    }

    @Test
    void aRegistrationIsReadCaseInsensitivelyOffTheForm() {
        assertThat(Registration.parse(" n412vd ")).isEqualTo(new Registration("N412VD"));
    }

    @Test
    void aSerialIsTheNumberTheAirframeKeeps() {
        assertThat(new Serial("5127")).hasToString("MSN 5127");
        assertThat(new Serial("5127").asInt()).isEqualTo(5127);
        assertThat(Serial.parse(" 5127 ")).isEqualTo(new Serial("5127"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "51", "512345", "51A7"})
    void aSerialRefusesAnythingElse(String number) {
        assertThatThrownBy(() -> new Serial(number))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a serial number");
    }

    @Test
    void aFindingIdPrintsPadded() {
        assertThat(new FindingId(2025, 4182)).hasToString("DR-2025-004182");
        assertThat(FindingId.parse("DR-2025-004182")).isEqualTo(new FindingId(2025, 4182));
    }

    @Test
    void aFindingIdStepsWithinItsYear() {
        assertThat(new FindingId(2025, 4182).nextInYear()).isEqualTo(new FindingId(2025, 4183));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "DR-2025-4182", "2025-004182", "DR-25-004182"})
    void aFindingIdRefusesANumberOffAnotherForm(String text) {
        assertThatThrownBy(() -> FindingId.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a damage report number");
    }

    @Test
    void aFindingYearAndSequenceAreBounded() {
        assertThatThrownBy(() -> new FindingId(1999, 1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a report year");
        assertThatThrownBy(() -> new FindingId(2025, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a report sequence");
    }

    @Test
    void aRepairIdIsFiledUnderItsChapter() {
        assertThat(new RepairId(53, 142)).hasToString("RP-53-0142");
        assertThat(RepairId.parse("rp-53-0142")).isEqualTo(new RepairId(53, 142));
        assertThatThrownBy(() -> new RepairId(34, 1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not carry repairs");
    }

    @Test
    void aPanelIdCarriesChapterSectionSequenceAndHand() {
        PanelId panel = new PanelId(53, 10, 142, "L");
        assertThat(panel).hasToString("5310-142L");
        assertThat(PanelId.parse("5310-142L")).isEqualTo(panel);
    }

    @Test
    void aPanelIdMirrorsAndLeavesACentrePanelAlone() {
        assertThat(new PanelId(53, 10, 142, "L").mirrored()).isEqualTo(new PanelId(53, 10, 142, "R"));
        assertThat(new PanelId(53, 10, 142, "C").mirrored()).isEqualTo(new PanelId(53, 10, 142, "C"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "5310-142", "5310142L", "5310-142X", "53-10-142L"})
    void aPanelIdRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> PanelId.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a panel number");
    }

    @Test
    void aPanelIdChecksEachFieldItWasHanded() {
        assertThatThrownBy(() -> new PanelId(34, 10, 142, "L"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not carry skin panels");
        assertThatThrownBy(() -> new PanelId(53, 10, 142, "X"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a hand");
    }
}
