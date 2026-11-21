package io.github.rkaushikdev.doublerbay.damage;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClusterTest {

    private static final Serial MSN = new Serial("5127");

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static Placed placed(int sequence, String along, String around, String size) {
        Dent dent = new Dent(inch("0.020"), inch(size), inch(size));
        Finding finding = new Finding(
                new FindingId(2025, sequence),
                MSN,
                DamageKind.DENT,
                dent,
                Sample.point(),
                LocalDate.of(2025, 3, 14),
                DiscoverySource.SCHEDULED_CHECK,
                DamageOrigin.IMPACT,
                usage(13900, 24400));
        return new Placed(finding, at(along, around));
    }

    private static Placed placed(int sequence, String along, String around) {
        return placed(sequence, along, around, "2.000");
    }

    @Test
    void aPlacedFindingReadsBackItsNumberAndWhereItIs() {
        assertThat(placed(1, "100.000", "50.000").toString()).startsWith("DR-2025-000001 at 100.000 along");
    }

    @Test
    void theExtentIsTheLongerSideOfWhatItCovers() {
        assertThat(placed(1, "100.000", "50.000", "3.000").extent()).isEqualTo(inch("3.000"));
    }

    @Test
    void theSeparationAskedForComesOffTheLargerOfTheTwo() {
        Placed small = placed(1, "100.000", "50.000", "1.500");
        Placed large = placed(2, "120.000", "50.000", "6.000");
        assertThat(Clusters.separationRequired(small, large)).isEqualTo(inch("6.000"));
        assertThat(Clusters.separationRequired(large, small)).isEqualTo(inch("6.000"));
    }

    @Test
    void twoTinyDamagesStillGetTheFloorBetweenThem() {
        Placed tiny = placed(1, "100.000", "50.000", "0.250");
        Placed other = placed(2, "104.000", "50.000", "0.250");
        assertThat(Clusters.separationRequired(tiny, other)).isEqualTo(Clusters.SEPARATION_FLOOR);
    }

    @Test
    void theGapIsMeasuredEdgeToEdgeAndNotCentreToCentre() {
        Placed first = placed(1, "100.000", "50.000", "2.000");
        Placed second = placed(2, "106.000", "50.000", "2.000");
        assertThat(Clusters.gapBetween(first, second)).isEqualTo(inch("4.000"));
    }

    @Test
    void damagesFurtherApartThanTheSeparationStandAlone() {
        Placed first = placed(1, "100.000", "50.000", "2.000");
        Placed far = placed(2, "110.000", "50.000", "2.000");
        assertThat(Clusters.interacts(first, far)).isFalse();
    }

    @Test
    void damagesCloserThanTheSeparationAreOneDamage() {
        Placed first = placed(1, "100.000", "50.000", "2.000");
        Placed near = placed(2, "103.000", "50.000", "2.000");
        assertThat(Clusters.interacts(first, near)).isTrue();
    }

    @Test
    void aBigNeighbourPullsASmallDamageInThatASmallOneWouldNot() {
        Placed small = placed(1, "100.000", "50.000", "1.000");
        Placed otherSmall = placed(2, "104.000", "50.000", "1.000");
        Placed big = placed(3, "104.000", "50.000", "6.000");
        assertThat(Clusters.interacts(small, otherSmall)).isFalse();
        assertThat(Clusters.interacts(small, big)).isTrue();
    }

    @Test
    void oneFindingOnItsOwnIsAClusterOfOne() {
        List<Cluster> found = Clusters.clusters(List.of(placed(1, "100.000", "50.000")));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).isSingle()).isTrue();
        assertThat(found.get(0).size()).isEqualTo(1);
    }

    @Test
    void twoFarApartFindingsStayTwoClusters() {
        List<Cluster> found =
                Clusters.clusters(List.of(placed(1, "100.000", "50.000"), placed(2, "130.000", "50.000")));
        assertThat(found).hasSize(2);
    }

    @Test
    void twoCloseFindingsComeBackAsOneCluster() {
        List<Cluster> found =
                Clusters.clusters(List.of(placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).size()).isEqualTo(2);
    }

    @Test
    void interactionCarriesThroughAMiddleDamage() {
        List<Placed> chain = List.of(
                placed(1, "100.000", "50.000", "2.000"),
                placed(2, "103.000", "50.000", "2.000"),
                placed(3, "106.000", "50.000", "2.000"));
        assertThat(Clusters.interacts(chain.get(0), chain.get(2))).isFalse();
        List<Cluster> found = Clusters.clusters(chain);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).size()).isEqualTo(3);
    }

    @Test
    void theEnvelopeOfAChainIsLargerThanAnyPairInIt() {
        List<Placed> chain = List.of(
                placed(1, "100.000", "50.000", "2.000"),
                placed(2, "103.000", "50.000", "2.000"),
                placed(3, "106.000", "50.000", "2.000"));
        Footprint envelope = Clusters.clusters(chain).get(0).envelope();
        assertThat(envelope.along()).isEqualTo(inch("8.000"));
        assertThat(envelope.around()).isEqualTo(inch("2.000"));
    }

    @Test
    void theEnvelopeIsCentredOnWhatItCovers() {
        List<Placed> chain = List.of(placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000"));
        Footprint envelope = Clusters.clusters(chain).get(0).envelope();
        assertThat(envelope.centre().along()).isEqualTo(inch("101.500"));
        assertThat(envelope.centre().around()).isEqualTo(inch("50.000"));
    }

    @Test
    void aSingleClusterEnvelopeIsTheDamageItself() {
        Cluster only = Clusters.clusters(List.of(placed(1, "100.000", "50.000", "3.000")))
                .get(0);
        assertThat(only.envelope().along()).isEqualTo(inch("3.000"));
    }

    @Test
    void theClusterNamesItsMembers() {
        Cluster found = Clusters.clusters(List.of(placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")))
                .get(0);
        assertThat(found.references()).containsExactlyInAnyOrder("DR-2025-000001", "DR-2025-000002");
    }

    @Test
    void theGoverningMemberIsTheLargestOne() {
        Cluster found = Clusters.clusters(
                        List.of(placed(1, "100.000", "50.000", "1.000"), placed(2, "102.000", "50.000", "6.000")))
                .get(0);
        assertThat(found.deepest().finding().reference()).isEqualTo(new FindingId(2025, 2));
    }

    @Test
    void aClusterReadsBackWhatItIs() {
        Cluster together = Clusters.clusters(List.of(placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")))
                .get(0);
        assertThat(together.toString()).startsWith("2 together, envelope");
        assertThat(Clusters.clusters(List.of(placed(1, "100.000", "50.000")))
                        .get(0)
                        .toString())
                .startsWith("DR-2025-000001");
    }

    @Test
    void anEmptyClusterIsRefused() {
        assertThatThrownBy(() -> new Cluster(List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a cluster has something in it");
    }

    @Test
    void nothingAtAllClustersIntoNothing() {
        assertThat(Clusters.clusters(List.of())).isEmpty();
    }
}
