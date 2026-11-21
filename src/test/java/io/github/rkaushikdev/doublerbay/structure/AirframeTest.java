package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AirframeTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    @Test
    void anAirframeReadsAsItsModelAndHowMuchIsOnIt() {
        assertThat(Sample.airframe()).hasToString("V320-200, 3 panels");
        assertThat(Sample.airframe().bayCount()).isEqualTo(18);
    }

    @Test
    void anAirframeNeedsAModelAndSomePanels() {
        assertThatThrownBy(() -> new Airframe("  ", Sample.grid(), List.of(Sample.forwardUpper())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("an airframe has a model");
        assertThatThrownBy(() -> new Airframe("V320-200", Sample.grid(), List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has panels on it");
    }

    @Test
    void twoPanelsWithTheSamePartNumberAreRefused() {
        assertThatThrownBy(() ->
                        new Airframe("V320-200", Sample.grid(), List.of(Sample.forwardUpper(), Sample.forwardUpper())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("same part number");
    }

    @Test
    void aPanelIsFoundByItsPartNumber() {
        PanelBuild build = Sample.airframe().buildFor(new PanelId(53, 10, 142, "L"));
        assertThat(build.panel().part()).isEqualTo(new PanelId(53, 10, 142, "L"));
    }

    @Test
    void aPartNumberNotOnTheTypeIsALookupMiss() {
        assertThatThrownBy(() -> Sample.airframe().buildFor(new PanelId(53, 10, 999, "L")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not a panel on the V320-200");
    }

    @Test
    void aWrittenPointLandsOnThePanelThatCoversItsBay() {
        PanelBuild build = Sample.airframe().panelAt(Sample.point());
        assertThat(build.panel().part()).isEqualTo(new PanelId(53, 10, 142, "L"));
    }

    @Test
    void aPointFurtherAftLandsOnTheNextPanelBack() {
        PanelBuild build = Sample.airframe().panelAt(Sample.point(43, "5.000", 2, "4.000"));
        assertThat(build.panel().part()).isEqualTo(new PanelId(53, 10, 143, "L"));
    }

    @Test
    void aPointFurtherDownLandsOnTheLowerPanel() {
        PanelBuild build = Sample.airframe().panelAt(Sample.point(41, "10.000", 5, "4.000"));
        assertThat(build.panel().part()).isEqualTo(new PanelId(53, 10, 152, "L"));
    }

    @Test
    void aPointOnNoPanelIsALookupMiss() {
        assertThatThrownBy(() -> Sample.airframe().panelAt(Sample.point(46, "10.000", 2, "4.000")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("covers");
    }

    @Test
    void resolvingGivesTheBayThePanelTheSpotAndTheMarginsAtOnce() {
        Resolved resolved = Sample.airframe().resolve(Sample.point());
        assertThat(resolved.bay()).hasToString("FR41-FR42 / S-2L-S-3L");
        assertThat(resolved.part()).isEqualTo(new PanelId(53, 10, 142, "L"));
        assertThat(resolved.spot().region()).isSameAs(Region.OPEN_FIELD);
        assertThat(resolved.margin().distance()).isEqualTo(inch("4.000"));
        assertThat(resolved.margins().margin()).isEqualTo(inch("10.000"));
    }

    @Test
    void theMemberMarginAndTheGoverningEdgeAreTwoDifferentNumbers() {
        Resolved resolved = Sample.airframe().resolve(Sample.point());
        assertThat(resolved.margin().distance()).isEqualTo(inch("4.000"));
        assertThat(resolved.margins().governing().to()).isEqualTo("FR41");
    }

    @Test
    void theSpotAndTheMarginAreTakenOffTheSameResolvedPoint() {
        Resolved resolved = Sample.airframe().resolve(Sample.point());
        assertThat(resolved.spot().thickness()).isEqualTo(inch("0.040"));
        assertThat(resolved.margin().member()).hasToString("S-2L");
    }

    @Test
    void aPointOnTheLapJointResolvesOntoItsRows() {
        Resolved resolved = Sample.airframe().resolve(Sample.point(41, "10.000", 4, "0.500"));
        assertThat(resolved.spot().region()).isSameAs(Region.FASTENER_ROW);
        assertThat(resolved.spot().feature()).contains("lap joint");
    }

    @Test
    void aPointByTheWindowResolvesIntoItsKeepOut() {
        Resolved resolved = Sample.airframe().resolve(Sample.point(42, "11.000", 2, "4.000"));
        assertThat(resolved.spot().region()).isSameAs(Region.CUTOUT_EDGE);
        assertThat(resolved.spot().feature()).isEqualTo("W12L");
    }

    @Test
    void aPointOverTheOldDoublerResolvesUnderIt() {
        Resolved resolved = Sample.airframe().resolve(Sample.point(41, "10.000", 5, "4.000"));
        assertThat(resolved.spot().region()).isSameAs(Region.UNDER_DOUBLER);
        assertThat(resolved.spot().feature()).isEqualTo("RP-53-0007-1");
    }

    @Test
    void aResolvedPointReadsBackAsPartBayAndSpot() {
        Resolved resolved = Sample.airframe().resolve(Sample.point());
        assertThat(resolved.toString()).startsWith("5310-142L FR41-FR42 / S-2L-S-3L, in open field");
    }

    @Test
    void theZonesAreWhatThePanelsSayTheyAre() {
        assertThat(Sample.airframe().zones()).isEqualTo(Set.of(new Zone(231), new Zone(131)));
        assertThat(Sample.airframe().inZone(new Zone(231))).hasSize(2);
        assertThat(Sample.airframe().inZone(new Zone(131))).hasSize(1);
        assertThat(Sample.airframe().inZone(new Zone(331))).isEmpty();
    }
}
