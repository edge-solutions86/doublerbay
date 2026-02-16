"""The check commands: what is on a tail, and what a check has to clear.

These two live together because they are the same conversation. The programme
list is the standing picture and the pack is that picture cut to a window, and
somebody planning a check reads one then the other.

The pack prints in three blocks with headings rather than one sorted table. A
planner is going to tear the page in half and give the inspections to the shop
and the deferrals to the engineer, and a single sorted list makes that a
copying exercise.
"""

from __future__ import annotations

import click

from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.ids import RepairId, Serial
from doublerbay.measure.usage import Cycles, Hours, Usage
from doublerbay.report.workpack import WorkPack, build
from doublerbay.store.unit import UnitOfWork

BLANK: tuple[str, ...] = ("", "", "")


@click.group()
def check() -> None:
    """What a check has to clear, and what the tail is carrying."""


def pack_rows(pack: WorkPack) -> list[tuple[str, ...]]:
    """The three blocks, with a heading on each and a blank line between."""
    rows: list[tuple[str, ...]] = []
    for heading, lines in (
        ("deferrals", pack.deferrals),
        ("inspections", pack.inspections),
        ("waiting on engineering", pack.waiting),
    ):
        if not lines:
            continue
        if rows:
            rows.append(BLANK)
        rows.append((heading.upper(), "", ""))
        rows.extend((("OVER" if one.overrun else ""), one.reference, one.detail) for one in lines)
    return rows


@check.command("pack")
@click.option("--tail", "serial", required=True)
@click.option("--opens", required=True, help="the day the aeroplane comes in")
@click.option("--closes", required=True, help="the day it goes back out")
@click.option("--within", type=int, default=None, help="pull work in from this many days ahead")
@click.pass_context
def check_pack(
    context: click.Context, serial: str, opens: str, closes: str, within: int | None
) -> None:
    """Everything one tail owes the desk at one check."""
    held = Serial(serial)
    with UnitOfWork(context.obj["engine"]) as unit:
        pack = build(unit, held, as_date(opens), as_date(closes), within)
    click.echo(str(pack))
    if pack.is_empty:
        return
    click.echo("")
    click.echo(columns(pack_rows(pack)))


@check.command("programme")
@click.option("--tail", "serial", required=True)
@click.option("--never-done", "unseen", is_flag=True, help="only the ones never signed off")
@click.pass_context
def check_programme(context: click.Context, serial: str, unseen: bool) -> None:
    """Every repeat inspection one tail is carrying."""
    held = Serial(serial)
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.aircraft.get(held)
        tasks = unit.inspections.never_done(held) if unseen else unit.inspections.on_tail(held)
    if not tasks:
        click.echo(say_nothing_found(f"on the programme for {serial}"))
        return
    rows: list[tuple[str, ...]] = [("repair", "part", "method", "threshold", "repeat", "last done")]
    rows.extend(
        (
            str(one.against),
            str(one.part),
            str(one.schedule.method),
            str(one.schedule.threshold),
            str(one.schedule.repeat) if one.schedule.repeat else "once only",
            one.last_done.isoformat() if one.last_done else "never",
        )
        for one in tasks
    )
    click.echo(columns(rows))


@check.command("due")
@click.option("--tail", "serial", required=True)
@click.option("--on", "on_day", required=True, help="the day to measure against")
@click.pass_context
def check_due(context: click.Context, serial: str, on_day: str) -> None:
    """The repeat inspections that have come due on one tail."""
    held = Serial(serial)
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.aircraft.get(held)
        flown = unit.aircraft.log_for(held).at(day)
        tasks = unit.inspections.due_on(held, flown, day)
    if not tasks:
        click.echo(say_nothing_found(f"due on {serial}"))
        return
    rows: list[tuple[str, ...]] = [("repair", "part", "method", "due")]
    rows.extend(
        (str(one.against), str(one.part), str(one.schedule.method), str(one.due())) for one in tasks
    )
    click.echo(columns(rows))


@check.command("done")
@click.argument("chapter", type=int)
@click.argument("sequence", type=int)
@click.option("--on", "on_day", required=True, help="the day it was done")
@click.option("--cycles", type=int, required=True)
@click.option("--hours", required=True)
@click.pass_context
def check_done(
    context: click.Context, chapter: int, sequence: int, on_day: str, cycles: int, hours: str
) -> None:
    """Sign off a repeat inspection, at the counters it was done at."""
    held = RepairId(chapter, sequence)
    flown = Usage(Cycles(cycles), Hours.of_hours(hours))
    with UnitOfWork(context.obj["engine"]) as unit:
        signed = unit.inspections.sign_off(held, as_date(on_day), flown)
        following = None if signed.is_a_one_off else str(signed.due())
    click.echo(f"signed off {held} at {flown}")
    if following is not None:
        click.echo(f"next due {following}")
