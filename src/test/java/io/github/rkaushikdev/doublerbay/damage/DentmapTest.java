package io.github.rkaushikdev.doublerbay.damage;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.TypeDefinition;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DentmapTest {

    private static final Serial MSN = new Serial("5127");

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static TypeDefinition definition() {
        return new TypeCatalogue().definitionFor(Series.STANDARD);
    }

    private static FuselagePoint point(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    private static FuselagePoint point() {
        return point(15, "10.000", 6, "0.000");
    }

    private static FuselagePoint atFrame(int frame) {
        return point(frame, "10.000", 6, "0.000");
    }

    private static Finding card(int sequence, FuselagePoint at, String size, DamageKind kind, Serial serial) {
        Geometry geometry = kind == DamageKind.DENT
                ? new Dent(inch("0.020"), inch(size), inch(size))
                : new Scoring(inch("0.008"), inch(size), inch("0.060"), Angle.ofDegrees(0));
        return new Finding(
                new FindingId(2025, sequence),
                serial,
                kind,
                geometry,
                at != null ? at : point(),
                LocalDate.of(2025, 3, 14),
                DiscoverySource.SCHEDULED_CHECK,
                DamageOrigin.IMPACT,
                usage(13900, 24400));
    }

    private static Finding card(int sequence, FuselagePoint at) {
        return card(sequence, at, "2.000", DamageKind.DENT, MSN);
    }

    private static Finding card(int sequence) {
        return card(sequence, null);
    }

    @Test
    void aCardPlacesOntoTheSkinItWasRaisedAgainst() {
        Placed landed = DentMap.place(definition(), card(1));
        assertThat(landed.centre().around()).isEqualTo(inch("54.600"));
        assertThat(landed.finding().reference()).isEqualTo(new FindingId(2025, 1));
    }

    @Test
    void aCardResolvesOntoThePanelItSitsOn() {
        PanelId part = DentMap.panelOf(definition(), card(1));
        assertThat(part).isInstanceOf(PanelId.class);
        assertThat(part.chapter()).isEqualTo(53);
    }

    @Test
    void theMapReadsBackHowMuchIsOpenAndWhere() {
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), card(2)));
        assertThat(built.count()).isEqualTo(2);
        assertThat(built.toString()).startsWith("MSN 5127: 2 open on 1 panels");
    }

    @Test
    void aCardFromAnotherTailIsRefused() {
        Finding elsewhere = card(1, null, "2.000", DamageKind.DENT, new Serial("5240"));
        assertThatThrownBy(() -> DentMap.of(MSN, definition(), List.of(elsewhere)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a card on");
    }

    @Test
    void anEmptyMapIsAnEmptyMap() {
        DentMap built = DentMap.of(MSN, definition(), List.of());
        assertThat(built.count()).isZero();
        assertThat(built.parts()).isEmpty();
        assertThat(built.clustersOn(new PanelId(53, 10, 100, "L"))).isEmpty();
    }

    @Test
    void cardsOnOnePanelCountTogether() {
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), card(2), card(3)));
        PanelId part = built.parts().get(0);
        assertThat(built.countOn(part)).isEqualTo(3);
        assertThat(built.countOn(new PanelId(53, 10, 999, "L"))).isZero();
    }

    @Test
    void theCountCanBeTakenByKind() {
        Finding scratch = card(2, null, "2.000", DamageKind.SCRATCH, MSN);
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), scratch));
        PanelId part = built.parts().get(0);
        assertThat(built.countOfKindOn(part, DamageKind.DENT)).isEqualTo(1);
        assertThat(built.countOfKindOn(part, DamageKind.SCRATCH)).isEqualTo(1);
        assertThat(built.countOfKindOn(part, DamageKind.CRACK)).isZero();
    }

    @Test
    void cardsOnDifferentPanelsGroupApart() {
        Finding farAft = card(2, atFrame(30));
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), farAft));
        assertThat(built.parts()).hasSize(2);
        for (PanelId part : built.parts()) {
            assertThat(built.countOn(part)).isEqualTo(1);
        }
    }

    @Test
    void closeCardsOnOnePanelComeBackAsOneCluster() {
        Finding near = card(2, point(15, "11.000", 6, "0.000"));
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), near));
        List<Cluster> groups = built.clustersOn(built.parts().get(0));
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(2);
    }

    @Test
    void farApartCardsOnOnePanelStaySeparateClusters() {
        Finding across = card(2, point(15, "10.000", 7, "6.000"));
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), across));
        List<Cluster> groups = built.clustersOn(built.parts().get(0));
        assertThat(groups).hasSize(2);
    }

    @Test
    void theClusterHoldingACardTakesItsNeighboursIn() {
        Finding near = card(2, point(15, "11.000", 6, "0.000"));
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), near));
        Cluster group = built.clusterHolding(card(1));
        assertThat(group.size()).isEqualTo(2);
        assertThat(group.references()).contains("DR-2025-000002");
    }

    @Test
    void aCardThatIsNotOnTheMapHasNoCluster() {
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1)));
        assertThatThrownBy(() -> built.clusterHolding(card(9, atFrame(30))))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on this map");
    }

    @Test
    void cardsCanBePulledOutByTheBayTheyFallIn() {
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1), card(2, atFrame(30))));
        Bay bay = definition().barrel().bayAt(point());
        assertThat(built.inBay(bay)).hasSize(1);
    }

    @Test
    void aBayWithNothingInItHasNothingInIt() {
        DentMap built = DentMap.of(MSN, definition(), List.of(card(1)));
        Bay otherBay = definition().barrel().bayAt(atFrame(30));
        assertThat(built.inBay(otherBay)).isEmpty();
    }
}
