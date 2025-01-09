import pytest

from doublerbay.errors import BadReading, DoublerbayError, NotOnFile, Refused, WrongMeasure
from doublerbay.ids import FindingId, PanelId, Registration, RepairId, Serial
from doublerbay.measure.length import Length


def test_every_refusal_is_also_the_builtin_a_caller_would_catch():
    assert issubclass(BadReading, ValueError)
    assert issubclass(WrongMeasure, TypeError)
    assert issubclass(NotOnFile, LookupError)
    for kind in (BadReading, WrongMeasure, NotOnFile, Refused):
        assert issubclass(kind, DoublerbayError)


def test_a_refusal_carries_its_reason_and_detail():
    refusal = Refused("no limit on file", "53-10-03 at revision 12")
    assert refusal.reason == "no limit on file"
    assert refusal.detail == "53-10-03 at revision 12"
    assert str(refusal) == "no limit on file: 53-10-03 at revision 12"


def test_a_refusal_without_detail_reads_as_its_reason():
    assert str(Refused("out of limits")) == "out of limits"
    assert Refused("out of limits").detail is None


def test_the_measure_modules_refuse_through_the_package_errors():
    with pytest.raises(BadReading):
        Length.of_inches("0.0405")
    with pytest.raises(WrongMeasure):
        Length(40.5)  # type: ignore[arg-type]


def test_the_old_builtin_still_catches_what_it_used_to():
    with pytest.raises(ValueError):
        Length.of_inches("0.0405")
    with pytest.raises(TypeError):
        Length(40.5)  # type: ignore[arg-type]


@pytest.mark.parametrize("marks", ["N412VD", "N1", "N99999", "N412V"])
def test_a_registration_takes_the_shapes_the_register_issues(marks):
    assert str(Registration(marks)) == marks


@pytest.mark.parametrize("marks", ["", "412VD", "N0412", "NVD", "N412VDX", "n412vd "])
def test_a_registration_refuses_anything_else(marks):
    with pytest.raises(BadReading, match="not a registration"):
        Registration(marks)


def test_a_registration_is_read_case_insensitively_off_the_form():
    assert Registration.parse(" n412vd ") == Registration("N412VD")


def test_a_serial_is_the_number_the_airframe_keeps():
    assert str(Serial("5127")) == "MSN 5127"
    assert Serial("5127").as_int == 5127
    assert Serial.parse(" 5127 ") == Serial("5127")


@pytest.mark.parametrize("number", ["", "51", "512345", "51A7"])
def test_a_serial_refuses_anything_else(number):
    with pytest.raises(BadReading, match="not a serial number"):
        Serial(number)


def test_a_finding_id_prints_padded():
    assert str(FindingId(2025, 4182)) == "DR-2025-004182"
    assert FindingId.parse("DR-2025-004182") == FindingId(2025, 4182)


def test_a_finding_id_steps_within_its_year():
    assert FindingId(2025, 4182).next_in_year() == FindingId(2025, 4183)


@pytest.mark.parametrize("text", ["", "DR-2025-4182", "2025-004182", "DR-25-004182"])
def test_a_finding_id_refuses_a_number_off_another_form(text):
    with pytest.raises(BadReading, match="not a damage report number"):
        FindingId.parse(text)


def test_a_finding_year_and_sequence_are_bounded():
    with pytest.raises(BadReading, match="not a report year"):
        FindingId(1999, 1)
    with pytest.raises(BadReading, match="not a report sequence"):
        FindingId(2025, 0)


def test_a_repair_id_is_filed_under_its_chapter():
    assert str(RepairId(53, 142)) == "RP-53-0142"
    assert RepairId.parse("rp-53-0142") == RepairId(53, 142)
    with pytest.raises(BadReading, match="does not carry repairs"):
        RepairId(34, 1)


def test_a_panel_id_carries_chapter_section_sequence_and_hand():
    panel = PanelId(53, 10, 142, "L")
    assert str(panel) == "5310-142L"
    assert PanelId.parse("5310-142L") == panel


def test_a_panel_id_mirrors_and_leaves_a_centre_panel_alone():
    assert PanelId(53, 10, 142, "L").mirrored() == PanelId(53, 10, 142, "R")
    assert PanelId(53, 10, 142, "C").mirrored() == PanelId(53, 10, 142, "C")


@pytest.mark.parametrize("text", ["", "5310-142", "5310142L", "5310-142X", "53-10-142L"])
def test_a_panel_id_refuses_anything_else(text):
    with pytest.raises(BadReading, match="not a panel number"):
        PanelId.parse(text)


def test_a_panel_id_checks_each_field_it_was_handed():
    with pytest.raises(BadReading, match="does not carry skin panels"):
        PanelId(34, 10, 142, "L")
    with pytest.raises(BadReading, match="is not a hand"):
        PanelId(53, 10, 142, "X")
