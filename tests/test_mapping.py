from datetime import date

import pytest

import sample
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading
from doublerbay.fleet.model import Bulletin
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.angle import Angle
from doublerbay.store.mapping import (
    aircraft_to_row,
    disposition_to_row,
    finding_to_row,
    geometry_from,
    log_to_rows,
    measurements_of,
    row_to_aircraft,
    row_to_disposition,
    row_to_finding,
    rows_to_log,
)

DR = FindingId(2025, 4182)


def inch(text):
    return sample.inch(text)


def deg(value):
    return Angle.of_degrees(value)


EVERY_GEOMETRY = {
    DamageKind.DENT: Dent(inch("0.020"), inch("3.000"), inch("2.000"), True, False),
    DamageKind.SCRATCH: Scoring(inch("0.003"), inch("2.000"), inch("0.060"), deg(45), True),
    DamageKind.GOUGE: Scoring(inch("0.006"), inch("1.000"), inch("0.090"), deg(90)),
    DamageKind.NICK: Scoring(inch("0.004"), inch("0.250"), inch("0.060"), deg(0)),
    DamageKind.CRACK: Crack(inch("0.750"), deg(10), False, True, 1),
    DamageKind.CORROSION: Corrosion(inch("2.000"), inch("1.000"), inch("0.006"), True, True),
    DamageKind.LIGHTNING_BURN: LightningBurn(
        inch("2.000"), inch("2.000"), 9, inch("0.008"), True, False
    ),
    DamageKind.HOLE: Opening(inch("0.500"), inch("0.500"), True, True),
    DamageKind.PUNCTURE: Opening(inch("0.750"), inch("0.500")),
    DamageKind.BUCKLE: Buckle(inch("6.000"), inch("0.120"), deg(90), True),
    DamageKind.DISBOND: Disbond(inch("3.000"), inch("2.000"), True, True),
}


def test_every_kind_of_measurement_set_round_trips_unchanged():
    for kind, geometry in EVERY_GEOMETRY.items():
        stored = measurements_of(geometry)
        assert geometry_from(kind, stored) == geometry


def test_the_stored_document_holds_whole_numbers_only():
    for geometry in EVERY_GEOMETRY.values():
        for value in measurements_of(geometry).values():
            assert isinstance(value, int | bool)


def test_a_stored_document_missing_a_number_is_refused():
    with pytest.raises(BadReading, match="a stored dent is missing its depth"):
        geometry_from(DamageKind.DENT, {"unexpected": 1})


def test_a_stored_length_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="not a stored length"):
        geometry_from(
            DamageKind.DENT,
            {"depth": "deep", "along": 3000, "around": 2000, "sharp_bottom": 0, "cracked": 0},
        )


def test_a_tail_round_trips_with_its_bulletins():
    before = sample.aircraft().with_embodied(Bulletin(53, 18))
    after = row_to_aircraft(aircraft_to_row(before))
    assert after == before


def test_a_withdrawn_tail_round_trips_with_the_day_it_went():
    before = sample.aircraft().withdrawn_on(date(2026, 6, 30))
    assert row_to_aircraft(aircraft_to_row(before)) == before


def test_a_log_round_trips_with_every_posting_on_it():
    before = sample.flying_log()
    after = rows_to_log(Serial("5127"), log_to_rows(before))
    assert after == before


def test_a_log_comes_back_in_order_however_the_rows_arrive():
    rows = log_to_rows(sample.flying_log())
    after = rows_to_log(Serial("5127"), list(reversed(rows)))
    assert after == sample.flying_log()


def test_a_barrel_card_round_trips_unchanged():
    before = sample.card()
    after = row_to_finding(finding_to_row(before))
    assert after == before


def test_a_wing_card_round_trips_unchanged():
    before = sample.card(at=sample.lower_wing_point())
    after = row_to_finding(finding_to_row(before))
    assert after == before


def test_a_card_at_a_later_revision_round_trips_with_its_revision():
    before = sample.card().re_measured(
        Dent(inch("0.040"), inch("3.000"), inch("2.000")), date(2025, 6, 1), sample.flown(1, 1)
    )
    after = row_to_finding(finding_to_row(before))
    assert after.revision == 2
    assert after == before


def test_a_superseded_card_round_trips_with_what_replaced_it():
    before = sample.card().superseded(FindingId(2025, 4200))
    assert row_to_finding(finding_to_row(before)).superseded_by == FindingId(2025, 4200)


def test_every_kind_of_card_round_trips():
    for kind, geometry in EVERY_GEOMETRY.items():
        origin = {
            DamageKind.CORROSION: DamageOrigin.ENVIRONMENT,
            DamageKind.LIGHTNING_BURN: DamageOrigin.LIGHTNING,
            DamageKind.CRACK: DamageOrigin.FATIGUE,
            DamageKind.DISBOND: DamageOrigin.ENVIRONMENT,
        }.get(kind, DamageOrigin.IMPACT)
        before = sample.card(kind=kind, geometry=geometry, origin=origin)
        assert row_to_finding(finding_to_row(before)) == before


def test_the_row_says_which_structure_the_location_belongs_to():
    assert finding_to_row(sample.card()).structure == "fuselage"
    assert finding_to_row(sample.card(at=sample.lower_wing_point())).structure == "wing"


def test_a_row_with_a_structure_nobody_recognises_is_refused():
    row = finding_to_row(sample.card())
    row.structure = "tailplane"
    with pytest.raises(BadReading, match="is not a structure on file"):
        row_to_finding(row)


def test_a_disposition_round_trips_with_its_whole_history():
    before = DispositionRecord.raised(DR)
    before = before.moved_to(DispositionState.ASSESSED, date(2025, 3, 14), "structures")
    before = before.moved_to(DispositionState.DEFERRED, date(2025, 4, 1), "structures", "parts")
    after = row_to_disposition(disposition_to_row(before))
    assert after == before


def test_a_disposition_with_no_history_round_trips():
    before = DispositionRecord.raised(DR)
    assert row_to_disposition(disposition_to_row(before)) == before


def test_the_moves_come_back_in_the_order_they_happened():
    before = DispositionRecord.raised(DR)
    before = before.moved_to(DispositionState.ASSESSED, date(2025, 3, 14), "structures")
    before = before.moved_to(DispositionState.IN_WORK, date(2025, 4, 1), "hangar")
    row = disposition_to_row(before)
    row.moves.reverse()
    assert row_to_disposition(row) == before
