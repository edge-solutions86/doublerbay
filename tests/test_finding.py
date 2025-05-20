from datetime import date

import pytest

import sample
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length

MSN = Serial("5127")
DR = FindingId(2025, 4182)
FOUND = date(2025, 3, 14)


def inch(text):
    return Length.of_inches(text)


def dent(depth="0.030"):
    return Dent(inch(depth), inch("3.000"), inch("2.000"))


def crack(length="0.750"):
    return Crack(inch(length), Angle.of_degrees(0))


def finding(
    kind=DamageKind.DENT,
    geometry=None,
    source=DiscoverySource.SCHEDULED_CHECK,
    origin=DamageOrigin.IMPACT,
    point=None,
    reference=DR,
):
    return Finding(
        reference=reference,
        serial=MSN,
        kind=kind,
        geometry=geometry if geometry is not None else dent(),
        point=point if point is not None else sample.point(),
        found_on=FOUND,
        source=source,
        origin=origin,
        at_usage=usage(13900, 24400),
    )


def test_a_finding_reads_back_its_number_tail_kind_and_place():
    assert str(finding()).startswith("DR-2025-004182 MSN 5127 dent at FR41")


def test_a_re_measured_finding_says_which_revision_it_is():
    later = finding().re_measured(dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    assert str(later).endswith("rev 2")
    assert later.revision == 2
    assert later.geometry.depth == inch("0.040")


def test_a_re_measurement_leaves_the_earlier_revision_alone():
    first = finding()
    first.re_measured(dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    assert first.revision == 1
    assert first.geometry.depth == inch("0.030")


def test_a_dent_card_carrying_crack_measurements_is_refused():
    with pytest.raises(BadReading, match="a dent is reported as dent measurements"):
        finding(geometry=crack())


def test_a_crack_card_carrying_dent_measurements_is_refused():
    with pytest.raises(BadReading, match="a crack is reported as crack measurements"):
        finding(kind=DamageKind.CRACK, origin=DamageOrigin.FATIGUE)


def test_the_three_scoring_kinds_all_take_the_same_measurements():
    for kind in (DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK):
        with pytest.raises(BadReading, match="scoring measurements"):
            finding(kind=kind)


def test_a_kind_and_a_cause_that_do_not_go_together_are_refused():
    with pytest.raises(BadReading, match="a dent does not come from lightning"):
        finding(origin=DamageOrigin.LIGHTNING)


def test_a_report_of_something_that_happened_needs_a_cause_that_happened():
    with pytest.raises(BadReading, match="names something that happened"):
        finding(source=DiscoverySource.GROUND_HANDLING, origin=DamageOrigin.UNKNOWN)


def test_a_report_of_something_that_happened_takes_an_event_cause():
    raised = finding(source=DiscoverySource.GROUND_HANDLING, origin=DamageOrigin.HANDLING)
    assert raised.source is DiscoverySource.GROUND_HANDLING


def test_a_finding_starts_at_revision_one():
    with pytest.raises(BadReading, match="starts at revision one"):
        Finding(
            reference=DR,
            serial=MSN,
            kind=DamageKind.DENT,
            geometry=dent(),
            point=sample.point(),
            found_on=FOUND,
            source=DiscoverySource.SCHEDULED_CHECK,
            origin=DamageOrigin.IMPACT,
            at_usage=usage(13900, 24400),
            revision=0,
        )


def test_a_finding_is_open_until_something_supersedes_it():
    assert finding().is_open
    closed = finding().superseded(FindingId(2025, 4200))
    assert not closed.is_open
    assert closed.superseded_by == FindingId(2025, 4200)


def test_a_finding_cannot_supersede_itself():
    with pytest.raises(BadReading, match="cannot supersede itself"):
        finding().superseded(DR)


def test_a_crack_finding_knows_it_has_no_allowable_size():
    cracked = finding(kind=DamageKind.CRACK, geometry=crack(), origin=DamageOrigin.FATIGUE)
    assert cracked.is_never_allowable
    assert not finding().is_never_allowable


def test_the_governing_depth_comes_off_whatever_kind_it_is():
    assert finding().governing_depth() == inch("0.030")


def test_a_kind_with_no_depth_refuses_to_give_one():
    cracked = finding(kind=DamageKind.CRACK, geometry=crack(), origin=DamageOrigin.FATIGUE)
    with pytest.raises(BadReading, match="not measured with a depth"):
        cracked.governing_depth()


def test_the_footprint_comes_off_the_geometry():
    centre = Offset(inch("630.000"), inch("20.000"))
    assert finding().footprint(centre).along == inch("3.000")


def test_a_barrel_finding_is_not_a_wing_finding():
    assert not finding().is_on_the_wing


def test_a_re_measurement_before_the_finding_is_refused():
    with pytest.raises(BadReading, match="before the finding"):
        finding().re_measured(dent("0.040"), date(2025, 1, 1), usage(13000, 24000))


def test_a_re_measurement_as_another_kind_of_geometry_is_refused():
    with pytest.raises(BadReading, match="not re measured as something else"):
        finding().re_measured(crack(), date(2025, 6, 1), usage(14200, 24900))
