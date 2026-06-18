"""The corrosion programme, from the terminal.

The programme report is the one that goes to the authority once a year, and
the unreported list is the one somebody has to look at every morning. They are
in the same place because they are the same programme, and a desk that has to
remember two commands will look at one of them.

The report prints its denominator on the page. A rate with no denominator on
it is a number somebody will quote in a meeting without it.
"""

from __future__ import annotations

import click

from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.ids import FindingId, Serial
from doublerbay.report.corrosion import programme_report
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionLevel

LEVELS = tuple(str(one.value) for one in CorrosionLevel)


def level_numbered(value: int) -> CorrosionLevel:
    """Read a level off its number, refusing one the programme does not grade."""
    try:
        return CorrosionLevel(value)
    except ValueError:
        raise click.BadParameter(f"{value} is not one of {', '.join(LEVELS)}") from None


@click.group()
def corrosion() -> None:
    """The corrosion prevention and control programme."""


@corrosion.command("list")
@click.option("--tail", "serial", default=None, help="one tail, or the whole fleet")
@click.option("--level", type=int, default=None, help="only findings graded at this level")
@click.pass_context
def corrosion_list(context: click.Context, serial: str | None, level: int | None) -> None:
    """Graded corrosion findings, on one tail or across the fleet."""
    with UnitOfWork(context.obj["engine"]) as unit:
        if serial:
            held = Serial(serial)
            unit.aircraft.get(held)
            entries = unit.corrosion.on_tail(held)
        else:
            entries = unit.corrosion.at_level(level_numbered(level or 1))
        if level is not None and serial:
            entries = tuple(one for one in entries if one.level.value == level)
    if not entries:
        click.echo(say_nothing_found("graded"))
        return
    rows: list[tuple[str, ...]] = [("part", "level", "found on", "report by")]
    rows.extend(
        (
            str(one.part),
            str(one.level.value),
            one.found_on.isoformat(),
            one.report_by.isoformat() if one.report_by else "",
        )
        for one in entries
    )
    click.echo(columns(rows))


@corrosion.command("unreported")
@click.option("--on", "on_day", required=True, help="the day to measure the clock against")
@click.pass_context
def corrosion_unreported(context: click.Context, on_day: str) -> None:
    """Level three findings the authority has not been told about yet."""
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        entries = unit.corrosion.unreported()
    if not entries:
        click.echo(say_nothing_found("waiting to be reported"))
        return
    rows: list[tuple[str, ...]] = [("part", "found on", "report by", "late")]
    rows.extend(
        (
            str(one.part),
            one.found_on.isoformat(),
            one.report_by.isoformat() if one.report_by else "",
            "yes" if one.is_overdue(day) else "no",
        )
        for one in entries
    )
    click.echo(columns(rows))


@corrosion.command("reported")
@click.argument("reference")
@click.option("--on", "on_day", required=True, help="the day it went out")
@click.pass_context
def corrosion_reported(context: click.Context, reference: str, on_day: str) -> None:
    """Record that a level three finding went out to the authority."""
    held = FindingId.parse(reference)
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.corrosion.mark_reported(held, as_date(on_day))
    click.echo(f"reported {held}")


@corrosion.command("programme")
@click.option("--tail", "serial", required=True)
@click.option("--since", required=True, help="the window opens")
@click.option("--until", required=True, help="the window closes")
@click.pass_context
def corrosion_programme(context: click.Context, serial: str, since: str, until: str) -> None:
    """One tail's programme picture over a window."""
    held = Serial(serial)
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.aircraft.get(held)
        entries = unit.corrosion.on_tail(held)
    report = programme_report(held, entries, as_date(since), as_date(until))
    rows = [
        ("tail", report.serial.number),
        ("window", f"{report.since.isoformat()} to {report.until.isoformat()}"),
        ("graded", str(report.total)),
        ("above level one", str(report.above_level_one)),
        ("escalation rate", str(report.escalation_rate)),
        ("over", report.denominator),
        ("coping", "yes" if report.is_coping else "no"),
    ]
    rows.extend((f"  level {level.value}", str(count)) for level, count in report.by_level)
    rows.extend(
        (f"  repeat {one.part}", f"{one.times} times over {one.years_between} years")
        for one in report.repeats
    )
    click.echo(columns(rows))
    if report.persistent:
        click.echo("")
        click.echo(f"{len(report.persistent)} locations keep corroding, review the programme")
