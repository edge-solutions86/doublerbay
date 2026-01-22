from datetime import date

from click.testing import CliRunner

import sample
from doublerbay.cli.main import cli
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId, RepairId
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = "2025-03-14"
FITTED = "2025-04-02"


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, date(2025, 3, 14), "a.okonkwo"
            )
        )
    return str(reference)


def draw(engine, reference, *extra):
    return invoke(
        engine,
        "repair",
        "draw",
        reference,
        "--along",
        "2.000",
        "--around",
        "2.000",
        "--on",
        DAY,
        *extra,
    )


def fit(engine, reference, sequence=1, centre=("468.000", "91.000"), *extra):
    return invoke(
        engine,
        "repair",
        "fit",
        reference,
        "--sequence",
        str(sequence),
        "--along",
        "2.000",
        "--around",
        "2.000",
        "--centre-along",
        centre[0],
        "--centre-around",
        centre[1],
        "--on",
        FITTED,
        "--by",
        "s.brandt",
        *extra,
    )


def test_a_drawing_prints_the_doubler():
    engine = stocked()
    printed = draw(engine, assessed(engine)).output
    assert "material" in printed
    assert "fasteners" in printed
    assert "edge distance" in printed


def test_a_drawing_writes_nothing():
    engine = stocked()
    draw(engine, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.on_tail(sample.MSN) == ()


def test_a_drawing_names_the_panel_it_landed_on():
    engine = stocked()
    assert "5310" in draw(engine, assessed(engine)).output


def test_a_temporary_drawing_says_it_is_temporary():
    engine = stocked()
    assert "temporary" in draw(engine, assessed(engine), "--temporary").output


def test_a_fastener_size_can_be_asked_for():
    engine = stocked()
    assert "dash 6" in draw(engine, assessed(engine), "--fastener", "6").output


def test_a_card_that_is_not_on_file_cannot_be_drawn_for():
    engine = stocked()
    assert draw(engine, "DR-2025-000099").exit_code != 0


def test_a_repair_can_be_fitted():
    engine = stocked()
    answer = fit(engine, assessed(engine))
    assert answer.exit_code == 0, answer.output
    assert "RP-53-0001 fitted" in answer.output


def test_fitting_closes_the_card():
    engine = stocked()
    fit(engine, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.CLOSED


def test_a_clash_is_printed_under_the_fit():
    engine = stocked()
    fit(engine, assessed(engine, 1), 1, ("468.000", "91.000"))
    printed = fit(engine, assessed(engine, 2), 2, ("469.000", "91.000")).output
    assert "too close: RP-53-0001 and RP-53-0002" in printed


def test_a_clean_fit_prints_no_clash_line():
    engine = stocked()
    assert "too close" not in fit(engine, assessed(engine)).output


def test_a_repair_number_used_twice_is_refused():
    engine = stocked()
    fit(engine, assessed(engine, 1), 1)
    assert fit(engine, assessed(engine, 2), 1).exit_code != 0


def test_a_measurement_that_is_not_one_is_refused():
    engine = stocked()
    answer = invoke(
        engine,
        "repair",
        "draw",
        assessed(engine),
        "--along",
        "big",
        "--around",
        "2.000",
        "--on",
        DAY,
    )
    assert answer.exit_code != 0


def test_the_repairs_on_a_tail_are_listed():
    engine = stocked()
    fit(engine, assessed(engine))
    listed = invoke(engine, "repair", "list", "--tail", "5127").output
    assert "RP-53-0001" in listed
    assert "DR-2025-000001" in listed


def test_a_tail_with_no_repairs_says_so():
    engine = stocked()
    listed = invoke(engine, "repair", "list", "--tail", "5127").output
    assert listed.strip() == "nothing fitted to 5127"


def test_a_tail_that_is_not_on_the_register_cannot_be_listed():
    engine = stocked()
    assert invoke(engine, "repair", "list", "--tail", "9999").exit_code != 0


def test_a_repair_can_be_taken_off():
    engine = stocked()
    fit(engine, assessed(engine))
    answer = invoke(engine, "repair", "remove", "53", "1", "--on", "2026-01-05")
    assert answer.exit_code == 0
    assert "removed RP-53-0001" in answer.output


def test_a_removed_repair_drops_off_the_fitted_list():
    engine = stocked()
    fit(engine, assessed(engine))
    invoke(engine, "repair", "remove", "53", "1", "--on", "2026-01-05")
    assert "nothing fitted" in invoke(engine, "repair", "list", "--tail", "5127").output


def test_a_removed_repair_is_still_on_the_full_list():
    engine = stocked()
    fit(engine, assessed(engine))
    invoke(engine, "repair", "remove", "53", "1", "--on", "2026-01-05")
    listed = invoke(engine, "repair", "list", "--tail", "5127", "--all").output
    assert "2026-01-05" in listed


def test_removing_one_that_is_not_on_file_is_refused():
    engine = stocked()
    assert invoke(engine, "repair", "remove", "53", "9", "--on", "2026-01-05").exit_code != 0


def test_a_repair_cannot_come_off_before_it_went_on():
    engine = stocked()
    fit(engine, assessed(engine))
    assert invoke(engine, "repair", "remove", "53", "1", "--on", "2024-01-01").exit_code != 0


def test_the_repair_on_file_carries_the_centre_it_was_fitted_at():
    engine = stocked()
    fit(engine, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.get(RepairId(53, 1)).centre.along == sample.inch("468.000")
