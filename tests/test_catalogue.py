from datetime import date

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.catalogue import TypeCatalogue, context_for
from doublerbay.fleet.model import Effectivity, Series
from doublerbay.fleet.register import Fleet
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog, usage
from doublerbay.ids import Registration, Serial
from doublerbay.measure.usage import Cycles
from doublerbay.v320.fuselage import FRAME_COUNT, barrel, barrel_for, section_of

DAY = date(2025, 1, 1)


def tail(serial="5127", marks="N412VD", series=Series.STANDARD):
    return Aircraft(
        serial=Serial(serial),
        registration=Registration(marks),
        series=series,
        line_number=1140,
        delivered=date(2016, 4, 12),
    )


def fleet():
    register = Fleet()
    register.enrol(
        tail(),
        UtilisationLog(
            Serial("5127"),
            (
                Snapshot(date(2024, 1, 1), usage(12000, 21000)),
                Snapshot(DAY, usage(13800, 24200)),
            ),
        ),
    )
    register.enrol(tail(serial="5301", marks="N155VD", series=Series.SHORT))
    return register


def test_the_short_series_barrel_is_shorter_and_nothing_else_changes():
    short = barrel_for(53)
    standard = barrel_for(FRAME_COUNT)
    assert len(short.grid.frames) == 53
    assert len(short.panels) < len(standard.panels)
    assert short.grid.stringers == standard.grid.stringers


def test_a_barrel_with_no_tail_cone_is_refused():
    with pytest.raises(BadReading, match="has no tail cone"):
        barrel_for(20)


def test_a_barrel_of_a_length_is_built_once_and_shared():
    assert barrel_for(53) is barrel_for(53)
    assert barrel_for(53) is not barrel_for(FRAME_COUNT)
    assert barrel() is barrel_for(FRAME_COUNT)


def test_the_sections_float_at_the_back_and_not_at_the_front():
    assert section_of(1, 53) == "forward"
    assert section_of(20, 53) == "centre"
    assert section_of(45, 53) == "aft"
    assert section_of(45, FRAME_COUNT) == "aft"


def test_the_catalogue_carries_every_series_the_fleet_flies():
    catalogue = TypeCatalogue()
    assert len(catalogue) == 3
    for series in Series:
        assert catalogue.carries(series)


def test_the_short_series_has_fewer_frames_on_file():
    catalogue = TypeCatalogue()
    assert catalogue.frames_on(Series.SHORT) < catalogue.frames_on(Series.STANDARD)
    assert catalogue.frames_on(Series.LONG_RANGE) == catalogue.frames_on(Series.STANDARD)


def test_a_series_off_the_catalogue_is_a_lookup_miss():
    catalogue = TypeCatalogue({Series.STANDARD: FRAME_COUNT})
    assert not catalogue.carries(Series.SHORT)
    with pytest.raises(NotOnFile, match="no structure definition on file"):
        catalogue.definition_for(Series.SHORT)
    with pytest.raises(NotOnFile, match="no structure definition on file"):
        catalogue.frames_on(Series.SHORT)


def test_a_definition_carries_a_barrel_and_a_wing():
    definition = TypeCatalogue().definition_for(Series.STANDARD)
    assert definition.barrel.panels
    assert definition.wing_box.panels
    assert str(definition).startswith("V320-200: V320-200")


def test_a_context_resolves_the_tail_the_counters_and_the_structure_together():
    context = context_for(fleet(), TypeCatalogue(), Serial("5127"), DAY)
    assert context.serial == Serial("5127")
    assert context.flown.cycles == Cycles(13800)
    assert context.barrel is TypeCatalogue().definition_for(Series.STANDARD).barrel
    assert context.wing_box.panels


def test_a_short_series_tail_resolves_onto_the_short_barrel():
    context = context_for(fleet(), TypeCatalogue(), Serial("5301"), DAY)
    assert len(context.barrel.grid.frames) == 53


def test_a_context_reads_back_the_tail_the_day_and_the_counters():
    context = context_for(fleet(), TypeCatalogue(), Serial("5127"), DAY)
    assert str(context) == "N412VD (MSN 5127, V320-200) on 2025-01-01, 13800 FC / 24200.0 FH"


def test_the_age_comes_off_the_tail_and_the_day():
    context = context_for(fleet(), TypeCatalogue(), Serial("5127"), DAY)
    assert context.age_years == tail().age_years(DAY)


def test_effectivity_is_answered_through_the_context():
    context = context_for(fleet(), TypeCatalogue(), Serial("5127"), DAY)
    assert context.covered_by(Effectivity(first_serial=5000, last_serial=5200))
    assert not context.covered_by(Effectivity(first_serial=5300))


def test_a_tail_not_in_service_on_the_day_has_no_context():
    with pytest.raises(NotOnFile, match="was not in service"):
        context_for(fleet(), TypeCatalogue(), Serial("5127"), date(2010, 1, 1))


def test_a_tail_off_the_register_has_no_context():
    with pytest.raises(NotOnFile, match="is not on the register"):
        context_for(fleet(), TypeCatalogue(), Serial("9999"), DAY)
