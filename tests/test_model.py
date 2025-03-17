import pytest

from doublerbay.errors import BadReading
from doublerbay.fleet.model import Bulletin, Effectivity, Series
from doublerbay.ids import Serial

SB_53_018 = Bulletin(53, 18)
SB_57_004 = Bulletin(57, 4)


def covers(effectivity, serial="5127", series=Series.STANDARD, embodied=()):
    return effectivity.covers(series, Serial(serial), frozenset(embodied))


def test_a_series_names_itself_the_way_the_manual_does():
    assert str(Series.STANDARD) == "V320-200"
    assert str(Series.LONG_RANGE) == "V320-200LR"
    assert Series.STANDARD.is_stretched
    assert not Series.SHORT.is_stretched


def test_a_bulletin_reads_and_prints_padded():
    assert str(SB_53_018) == "SB 53-018"
    assert Bulletin.parse("SB 53-018") == SB_53_018
    assert Bulletin.parse("sb53-018") == SB_53_018


@pytest.mark.parametrize("text", ["", "53-018", "SB 53-18", "SB 530-18", "bulletin"])
def test_a_bulletin_refuses_anything_else(text):
    with pytest.raises(BadReading, match="not a bulletin number"):
        Bulletin.parse(text)


def test_a_bulletin_chapter_and_number_are_bounded():
    with pytest.raises(BadReading, match="does not carry bulletins"):
        Bulletin(10, 18)
    with pytest.raises(BadReading, match="not a bulletin number"):
        Bulletin(53, 0)


def test_effectivity_with_nothing_set_covers_the_whole_fleet():
    everything = Effectivity.all_aircraft()
    assert covers(everything)
    assert covers(everything, series=Series.SHORT)
    assert covers(everything, serial="999")
    assert everything.is_open_ended


def test_a_series_outside_the_list_is_not_covered():
    only_long = Effectivity(series=frozenset({Series.LONG_RANGE}))
    assert covers(only_long, series=Series.LONG_RANGE)
    assert not covers(only_long, series=Series.STANDARD)


def test_effectivity_against_no_series_is_refused():
    with pytest.raises(BadReading, match="applies to nothing"):
        Effectivity(series=frozenset())


def test_a_serial_range_takes_both_its_ends():
    block = Effectivity(first_serial=5001, last_serial=5240)
    assert covers(block, serial="5001")
    assert covers(block, serial="5240")
    assert not covers(block, serial="5000")
    assert not covers(block, serial="5241")
    assert not block.is_open_ended


def test_an_open_ended_range_runs_on_from_its_first_serial():
    from_here = Effectivity(first_serial=5200)
    assert covers(from_here, serial="9999")
    assert not covers(from_here, serial="5199")
    assert from_here.is_open_ended


def test_a_range_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="run backwards"):
        Effectivity(first_serial=5240, last_serial=5001)


def test_a_required_bulletin_has_to_be_embodied():
    after_mod = Effectivity(requires=SB_53_018)
    assert covers(after_mod, embodied=[SB_53_018])
    assert not covers(after_mod, embodied=[])
    assert not covers(after_mod, embodied=[SB_57_004])


def test_an_excluded_bulletin_has_to_be_absent():
    before_mod = Effectivity(excludes=SB_53_018)
    assert covers(before_mod, embodied=[])
    assert covers(before_mod, embodied=[SB_57_004])
    assert not covers(before_mod, embodied=[SB_53_018])


def test_the_same_bulletin_cannot_be_required_and_excluded():
    with pytest.raises(BadReading, match="both required and excluded"):
        Effectivity(requires=SB_53_018, excludes=SB_53_018)


def test_every_clause_has_to_be_satisfied_at_once():
    narrow = Effectivity(
        series=frozenset({Series.STANDARD}),
        first_serial=5100,
        last_serial=5200,
        requires=SB_53_018,
    )
    assert covers(narrow, serial="5150", embodied=[SB_53_018])
    assert not covers(narrow, serial="5150", embodied=[])
    assert not covers(narrow, serial="5300", embodied=[SB_53_018])
    assert not covers(narrow, serial="5150", series=Series.SHORT, embodied=[SB_53_018])


def test_effectivity_reads_back_the_way_the_page_prints_it():
    assert str(Effectivity(series=frozenset({Series.STANDARD}))) == "200"
    narrow = Effectivity(
        series=frozenset({Series.STANDARD}),
        first_serial=5100,
        last_serial=5200,
        requires=SB_53_018,
    )
    assert str(narrow) == "200; MSN 5100 to 5200; with SB 53-018"


def test_an_open_ended_range_says_so_when_it_prints():
    assert "MSN 5100 to on" in str(Effectivity(first_serial=5100))
    assert "MSN first to 5200" in str(Effectivity(last_serial=5200))
    assert "without SB 53-018" in str(Effectivity(excludes=SB_53_018))
