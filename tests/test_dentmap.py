from datetime import date

import pytest

import sample
from doublerbay.damage.dent import Dent
from doublerbay.damage.dentmap import DentMap, panel_of, place
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.scoring import Scoring
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.model import Series
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.zone import Side
from doublerbay.ids import FindingId, PanelId, Serial
from doublerbay.measure.angle import Angle

MSN = Serial("5127")


def inch(text):
    return sample.inch(text)


def definition():
    return TypeCatalogue().definition_for(Series.STANDARD)


def point(frame=15, aft="10.000", stringer=6, down="0.000"):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(stringer, Side.LEFT), inch(down))


def card(sequence=1, at=None, size="2.000", kind=DamageKind.DENT, serial=MSN):
    geometry = (
        Dent(inch("0.020"), inch(size), inch(size))
        if kind is DamageKind.DENT
        else Scoring(inch("0.008"), inch(size), inch("0.060"), Angle.of_degrees(0))
    )
    return Finding(
        reference=FindingId(2025, sequence),
        serial=serial,
        kind=kind,
        geometry=geometry,
        point=at if at is not None else point(),
        found_on=date(2025, 3, 14),
        source=DiscoverySource.SCHEDULED_CHECK,
        origin=DamageOrigin.IMPACT,
        at_usage=usage(13900, 24400),
    )


def test_a_card_places_onto_the_skin_it_was_raised_against():
    landed = place(definition(), card())
    assert landed.centre.around == inch("54.600")
    assert landed.finding.reference == FindingId(2025, 1)


def test_a_card_resolves_onto_the_panel_it_sits_on():
    part = panel_of(definition(), card())
    assert isinstance(part, PanelId)
    assert part.chapter == 53


def test_the_map_reads_back_how_much_is_open_and_where():
    built = DentMap.of(MSN, definition(), (card(1), card(2)))
    assert built.count == 2
    assert str(built).startswith("MSN 5127: 2 open on 1 panels")


def test_a_card_from_another_tail_is_refused():
    with pytest.raises(BadReading, match="is not a card on"):
        DentMap.of(MSN, definition(), (card(1, serial=Serial("5240")),))


def test_an_empty_map_is_an_empty_map():
    built = DentMap.of(MSN, definition(), ())
    assert built.count == 0
    assert built.parts == ()
    assert built.clusters_on(PanelId(53, 10, 100, "L")) == ()


def test_cards_on_one_panel_count_together():
    built = DentMap.of(MSN, definition(), (card(1), card(2), card(3)))
    part = built.parts[0]
    assert built.count_on(part) == 3
    assert built.count_on(PanelId(53, 10, 999, "L")) == 0


def test_the_count_can_be_taken_by_kind():
    built = DentMap.of(MSN, definition(), (card(1), card(2, kind=DamageKind.SCRATCH)))
    part = built.parts[0]
    assert built.count_of_kind_on(part, DamageKind.DENT) == 1
    assert built.count_of_kind_on(part, DamageKind.SCRATCH) == 1
    assert built.count_of_kind_on(part, DamageKind.CRACK) == 0


def test_cards_on_different_panels_group_apart():
    far_aft = card(2, at=point(frame=30))
    built = DentMap.of(MSN, definition(), (card(1), far_aft))
    assert len(built.parts) == 2
    for part in built.parts:
        assert built.count_on(part) == 1


def test_close_cards_on_one_panel_come_back_as_one_cluster():
    near = card(2, at=point(aft="11.000"))
    built = DentMap.of(MSN, definition(), (card(1), near))
    groups = built.clusters_on(built.parts[0])
    assert len(groups) == 1
    assert groups[0].size == 2


def test_far_apart_cards_on_one_panel_stay_separate_clusters():
    across = card(2, at=point(stringer=7, down="6.000"))
    built = DentMap.of(MSN, definition(), (card(1), across))
    groups = built.clusters_on(built.parts[0])
    assert len(groups) == 2


def test_the_cluster_holding_a_card_takes_its_neighbours_in():
    near = card(2, at=point(aft="11.000"))
    built = DentMap.of(MSN, definition(), (card(1), near))
    group = built.cluster_holding(card(1))
    assert group.size == 2
    assert "DR-2025-000002" in group.references()


def test_a_card_that_is_not_on_the_map_has_no_cluster():
    built = DentMap.of(MSN, definition(), (card(1),))
    with pytest.raises(NotOnFile, match="is not on this map"):
        built.cluster_holding(card(9, at=point(frame=30)))


def test_cards_can_be_pulled_out_by_the_bay_they_fall_in():
    built = DentMap.of(MSN, definition(), (card(1), card(2, at=point(frame=30))))
    bay = definition().barrel.bay_at(point())
    assert len(built.in_bay(bay)) == 1


def test_a_bay_with_nothing_in_it_has_nothing_in_it():
    built = DentMap.of(MSN, definition(), (card(1),))
    other_bay = definition().barrel.bay_at(point(frame=30))
    assert built.in_bay(other_bay) == ()
