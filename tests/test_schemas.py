from datetime import date

import pytest

import sample
from doublerbay.api.schemas import (
    AircraftIn,
    FindingIn,
    FuselageLocationIn,
    UsageIn,
    WingLocationIn,
    aircraft_out,
    assessment_out,
    finding_out,
    inches,
    measurements_in,
    measurements_out,
)
from doublerbay.assess.assessor import assess
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length

DR = FindingId(2025, 4182)


def card_body(**overrides):
    body = {
        "serial": "5127",
        "kind": "dent",
        "origin": "impact",
        "source": "scheduled check",
        "found_on": "2025-03-14",
        "at_usage": {"cycles": 13900, "hours": "24400.0"},
        "fuselage": {
            "structure": "fuselage",
            "frame": 15,
            "aft_of_frame": "10.000",
            "stringer": 10,
            "below_stringer": "0.000",
            "side": "L",
        },
        "measurements": {
            "depth": "0.020",
            "along": "3.000",
            "around": "2.000",
            "sharp_bottom": False,
            "cracked": False,
        },
    }
    body.update(overrides)
    return body


def test_a_length_is_parsed_off_the_wire_the_way_it_is_off_a_card():
    assert inches("0.040") == Length.of_inches("0.040")
    assert inches("727.5") == Length.of_inches("727.5")


def test_a_length_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="is not a length in inches"):
        inches("deep")


def test_a_measurement_set_goes_out_with_its_lengths_as_text():
    sent = measurements_out(Dent(Length.of_inches("0.020"), Length(3000), Length(2000)))
    assert sent["depth"] == "0.020"
    assert sent["along"] == "3.000"
    assert sent["sharp_bottom"] is False


def test_a_bearing_goes_out_in_degrees_to_a_tenth():
    sent = measurements_out(Crack(Length(750), Angle.of_degrees("12.5")))
    assert sent["bearing"] == "12.5"
    assert sent["tips_found"] == 2


def test_a_measurement_set_round_trips_through_the_wire_form():
    before = Dent(Length.of_inches("0.020"), Length(3000), Length(2000), True, False)
    assert measurements_in(DamageKind.DENT, measurements_out(before)) == before


def test_a_bearing_round_trips_through_the_wire_form():
    before = Crack(Length(750), Angle.of_degrees("12.5"), False, True, 1)
    assert measurements_in(DamageKind.CRACK, measurements_out(before)) == before


def test_a_measurement_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="is not a depth in inches"):
        measurements_in(
            DamageKind.DENT,
            {
                "depth": "deep",
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            },
        )


def test_a_bearing_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="is not a bearing in degrees"):
        measurements_in(
            DamageKind.CRACK,
            {
                "length": "0.750",
                "bearing": "sideways",
                "through_thickness": True,
                "stop_drilled": False,
                "tips_found": 2,
            },
        )


def test_a_counter_reading_crosses_with_its_hours_as_text():
    reading = UsageIn(cycles=13900, hours="24400.5").to_domain()
    assert reading.cycles.count == 13900
    assert str(reading.hours) == "24400.5 FH"


def test_a_counter_reading_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="is not a count of hours"):
        UsageIn(cycles=1, hours="lots").to_domain()


def test_a_fuselage_location_arrives_as_a_written_point():
    location = FuselageLocationIn(
        frame=15, aft_of_frame="10.000", stringer=10, below_stringer="0.000", side="L"
    ).to_domain()
    assert isinstance(location, FuselagePoint)
    assert str(location) == "FR15 10.000 aft, S-10L on"


def test_a_wing_location_arrives_as_a_written_point():
    location = WingLocationIn(
        rib=2,
        outboard_of_rib="6.000",
        spar="front",
        aft_of_spar="20.000",
        surface="lower",
        side="L",
    ).to_domain()
    assert isinstance(location, WingPoint)
    assert location.surface.value == "lower"


def test_a_card_arrives_and_becomes_a_finding():
    finding = FindingIn(**card_body()).to_domain(DR)
    assert finding.reference == DR
    assert finding.kind is DamageKind.DENT
    assert finding.geometry.depth == Length.of_inches("0.020")


def test_a_card_with_no_location_is_refused():
    body = card_body()
    body.pop("fuselage")
    with pytest.raises(BadReading, match="says where it is"):
        FindingIn(**body).to_domain(DR)


def test_a_card_on_two_structures_at_once_is_refused():
    body = card_body(
        wing={
            "structure": "wing",
            "rib": 2,
            "outboard_of_rib": "6.000",
            "spar": "front",
            "aft_of_spar": "20.000",
            "surface": "lower",
            "side": "L",
        }
    )
    with pytest.raises(BadReading, match="says where it is"):
        FindingIn(**body).to_domain(DR)


def test_a_card_goes_back_with_everything_needed_to_read_it():
    sent = finding_out(sample.card())
    assert sent.reference == "DR-2025-000001"
    assert sent.serial == "5127"
    assert sent.location.structure == "fuselage"
    assert sent.measurements["depth"] == "0.020"
    assert sent.superseded_by is None


def test_a_superseded_card_says_what_replaced_it():
    sent = finding_out(sample.card().superseded(FindingId(2025, 4200)))
    assert sent.superseded_by == "DR-2025-004200"


def test_an_assessment_goes_back_with_its_whole_trail():
    answer = assess(v320_book(), sample.tail_context(), sample.card())
    sent = assessment_out(answer)
    assert sent.reference == "DR-2025-000001"
    assert sent.part.startswith("5310-")
    assert sent.thickness.startswith("0.0")
    assert sent.verdict.reasons
    assert sent.verdict.disposition


def test_a_tail_goes_back_and_comes_in_again_unchanged():
    sent = aircraft_out(sample.aircraft())
    back = AircraftIn(
        serial=sent.serial,
        registration=sent.registration,
        series=sent.series,
        line_number=sent.line_number,
        delivered=sent.delivered,
    ).to_domain()
    assert back == sample.aircraft()


def test_a_tail_with_a_line_number_of_nothing_is_refused_by_the_schema():
    with pytest.raises(ValueError):
        AircraftIn(
            serial="5127",
            registration="N412VD",
            series="200",
            line_number=0,
            delivered=date(2016, 4, 12),
        )
