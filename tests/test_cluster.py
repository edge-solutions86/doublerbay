from datetime import date

import pytest

import sample
from doublerbay.damage.cluster import (
    SEPARATION_FLOOR,
    Cluster,
    Placed,
    clusters,
    gap_between,
    interacts,
    separation_required,
)
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, Serial

MSN = Serial("5127")


def inch(text):
    return sample.inch(text)


def at(along, around):
    return Offset(inch(along), inch(around))


def placed(sequence, along, around, size="2.000"):
    dent = Dent(inch("0.020"), inch(size), inch(size))
    finding = Finding(
        reference=FindingId(2025, sequence),
        serial=MSN,
        kind=DamageKind.DENT,
        geometry=dent,
        point=sample.point(),
        found_on=date(2025, 3, 14),
        source=DiscoverySource.SCHEDULED_CHECK,
        origin=DamageOrigin.IMPACT,
        at_usage=usage(13900, 24400),
    )
    return Placed(finding, at(along, around))


def test_a_placed_finding_reads_back_its_number_and_where_it_is():
    assert str(placed(1, "100.000", "50.000")).startswith("DR-2025-000001 at 100.000 along")


def test_the_extent_is_the_longer_side_of_what_it_covers():
    assert placed(1, "100.000", "50.000", size="3.000").extent == inch("3.000")


def test_the_separation_asked_for_comes_off_the_larger_of_the_two():
    small = placed(1, "100.000", "50.000", size="1.500")
    large = placed(2, "120.000", "50.000", size="6.000")
    assert separation_required(small, large) == inch("6.000")
    assert separation_required(large, small) == inch("6.000")


def test_two_tiny_damages_still_get_the_floor_between_them():
    tiny = placed(1, "100.000", "50.000", size="0.250")
    other = placed(2, "104.000", "50.000", size="0.250")
    assert separation_required(tiny, other) == SEPARATION_FLOOR


def test_the_gap_is_measured_edge_to_edge_and_not_centre_to_centre():
    first = placed(1, "100.000", "50.000", size="2.000")
    second = placed(2, "106.000", "50.000", size="2.000")
    assert gap_between(first, second) == inch("4.000")


def test_damages_further_apart_than_the_separation_stand_alone():
    first = placed(1, "100.000", "50.000", size="2.000")
    far = placed(2, "110.000", "50.000", size="2.000")
    assert not interacts(first, far)


def test_damages_closer_than_the_separation_are_one_damage():
    first = placed(1, "100.000", "50.000", size="2.000")
    near = placed(2, "103.000", "50.000", size="2.000")
    assert interacts(first, near)


def test_a_big_neighbour_pulls_a_small_damage_in_that_a_small_one_would_not():
    small = placed(1, "100.000", "50.000", size="1.000")
    other_small = placed(2, "104.000", "50.000", size="1.000")
    big = placed(3, "104.000", "50.000", size="6.000")
    assert not interacts(small, other_small)
    assert interacts(small, big)


def test_one_finding_on_its_own_is_a_cluster_of_one():
    found = clusters([placed(1, "100.000", "50.000")])
    assert len(found) == 1
    assert found[0].is_single
    assert found[0].size == 1


def test_two_far_apart_findings_stay_two_clusters():
    found = clusters([placed(1, "100.000", "50.000"), placed(2, "130.000", "50.000")])
    assert len(found) == 2


def test_two_close_findings_come_back_as_one_cluster():
    found = clusters([placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")])
    assert len(found) == 1
    assert found[0].size == 2


def test_interaction_carries_through_a_middle_damage():
    chain = [
        placed(1, "100.000", "50.000", size="2.000"),
        placed(2, "103.000", "50.000", size="2.000"),
        placed(3, "106.000", "50.000", size="2.000"),
    ]
    assert not interacts(chain[0], chain[2])
    found = clusters(chain)
    assert len(found) == 1
    assert found[0].size == 3


def test_the_envelope_of_a_chain_is_larger_than_any_pair_in_it():
    chain = [
        placed(1, "100.000", "50.000", size="2.000"),
        placed(2, "103.000", "50.000", size="2.000"),
        placed(3, "106.000", "50.000", size="2.000"),
    ]
    envelope = clusters(chain)[0].envelope
    assert envelope.along == inch("8.000")
    assert envelope.around == inch("2.000")


def test_the_envelope_is_centred_on_what_it_covers():
    chain = [placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")]
    envelope = clusters(chain)[0].envelope
    assert envelope.centre.along == inch("101.500")
    assert envelope.centre.around == inch("50.000")


def test_a_single_cluster_envelope_is_the_damage_itself():
    only = clusters([placed(1, "100.000", "50.000", size="3.000")])[0]
    assert only.envelope.along == inch("3.000")


def test_the_cluster_names_its_members():
    found = clusters([placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")])[0]
    assert set(found.references()) == {"DR-2025-000001", "DR-2025-000002"}


def test_the_governing_member_is_the_largest_one():
    found = clusters(
        [
            placed(1, "100.000", "50.000", size="1.000"),
            placed(2, "102.000", "50.000", size="6.000"),
        ]
    )[0]
    assert found.deepest().finding.reference == FindingId(2025, 2)


def test_a_cluster_reads_back_what_it_is():
    together = clusters([placed(1, "100.000", "50.000"), placed(2, "103.000", "50.000")])[0]
    assert str(together).startswith("2 together, envelope")
    assert str(clusters([placed(1, "100.000", "50.000")])[0]).startswith("DR-2025-000001")


def test_an_empty_cluster_is_refused():
    with pytest.raises(BadReading, match="a cluster has something in it"):
        Cluster(())


def test_nothing_at_all_clusters_into_nothing():
    assert clusters([]) == ()
