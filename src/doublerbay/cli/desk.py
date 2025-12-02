"""The commands that decide things, and the commands that print lists.

Deferring is two writes and they belong together. The deferral itself is what
the aeroplane flies against, and the disposition move is what makes the card
show up as deferred on every report. One without the other is a card that is
either flying against a limit nobody reports on, or reported as deferred with
no limit behind it. Both have happened on real fleets.

A card has to be assessed before it can be deferred. That is the domain's
rule, not this module's, and the refusal comes back with the states it would
not move between. It reads like an obstruction the first time somebody hits it
at six in the morning, and it is the reason nobody defers a crack they have
not looked up.
"""

from __future__ import annotations

from datetime import date

import click

from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionState
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.report.desk import (
    due_across_fleet,
    due_on_tail,
    grounded,
    open_across_fleet,
    open_on_tail,
)
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.method import InspectionMethod

METHODS = tuple(one.name.lower() for one in InspectionMethod)


def method_named(text: str) -> InspectionMethod:
    """Read a method off the short name the shop uses rather than the long one."""
    try:
        return InspectionMethod[text.strip().upper()]
    except KeyError:
        raise click.BadParameter(f"{text!r} is not one of {', '.join(METHODS)}") from None


@click.group()
def defer() -> None:
    """Deferrals: grant them, tighten them, lift them."""


@defer.command("grant")
@click.argument("reference")
@click.option("--by", required=True, help="who is granting it")
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.option("--method", default="dvi", show_default=True, help=f"one of {', '.join(METHODS)}")
@click.option("--limit", default=None, help='for example "300 FC / 90 DY"')
@click.option("--until-check", default=None, help="tighten to a check on this day")
@click.option("--reason", default="")
@click.pass_context
def defer_grant(
    context: click.Context,
    reference: str,
    by: str,
    on_day: str,
    method: str,
    limit: str | None,
    until_check: str | None,
    reason: str,
) -> None:
    """Defer a card, writing the limit and the disposition move together."""
    held = FindingId.parse(reference)
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        finding = unit.findings.latest(held)
        flown = unit.aircraft.log_for(finding.serial).at(day)
        deferral = grant(
            against=held,
            granted=day,
            at_usage=flown,
            by=by,
            method=method_named(method),
            engineer_set=Interval.parse(limit) if limit else None,
            next_check=as_date(until_check) if until_check else None,
            reason=reason,
        )
        unit.deferrals.grant(deferral)
        record = unit.dispositions.get(held)
        unit.dispositions.save(record.moved_to(DispositionState.DEFERRED, day, by, reason))
    click.echo(f"deferred {held} until {deferral.due}")


@defer.command("tighten")
@click.argument("reference")
@click.option("--limit", required=True, help='the shorter limit, for example "50 FC"')
@click.option("--by", required=True)
@click.option("--reason", default="")
@click.pass_context
def defer_tighten(context: click.Context, reference: str, limit: str, by: str, reason: str) -> None:
    """Cut a live deferral back to a shorter limit."""
    held = FindingId.parse(reference)
    with UnitOfWork(context.obj["engine"]) as unit:
        current = unit.deferrals.get(held)
        unit.deferrals.tighten(current.tightened_to(Interval.parse(limit), by, reason))
        after = unit.deferrals.get(held)
    click.echo(f"tightened {held} to {after.due}")


@defer.command("lift")
@click.argument("reference")
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.pass_context
def defer_lift(context: click.Context, reference: str, on_day: str) -> None:
    """Close a deferral because the damage under it has been dealt with."""
    held = FindingId.parse(reference)
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.deferrals.lift(held, as_date(on_day))
    click.echo(f"lifted {held}")


@defer.command("show")
@click.argument("reference")
@click.pass_context
def defer_show(context: click.Context, reference: str) -> None:
    """One live deferral, with what it is due against."""
    with UnitOfWork(context.obj["engine"]) as unit:
        held = unit.deferrals.get(FindingId.parse(reference))
    click.echo(
        columns(
            [
                ("against", str(held.against)),
                ("granted", held.granted.isoformat()),
                ("granted by", held.by),
                ("at", str(held.at_usage)),
                ("limit", str(held.limit)),
                ("due", str(held.due)),
                ("re inspect", str(held.re_inspect_with)),
                ("reason", held.reason or "not given"),
            ]
        )
    )


@click.group()
def report() -> None:
    """The lists the desk prints: what is due, what is open, what is grounded."""


def due_rows(items) -> list[tuple[str, ...]]:
    rows: list[tuple[str, ...]] = [("tail", "card", "due", "margin", "what")]
    rows.extend(
        (
            one.serial.number,
            str(one.against),
            str(one.due),
            str(one.margin),
            one.what,
        )
        for one in items
    )
    return rows


@report.command("due")
@click.option("--tail", "serial", default=None, help="one tail, or leave it off for the fleet")
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.option("--within", type=int, default=None, help="only what falls due inside this many days")
@click.pass_context
def report_due(context: click.Context, serial: str | None, on_day: str, within: int | None) -> None:
    """What falls due, worst first."""
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        if serial:
            items = due_on_tail(unit, Serial(serial), day, within).items
        else:
            items = tuple(
                one for report in due_across_fleet(unit, day, within) for one in report.items
            )
    if not items:
        click.echo(say_nothing_found("due"))
        return
    click.echo(columns(due_rows(items)))


@report.command("grounded")
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.pass_context
def report_grounded(context: click.Context, on_day: str) -> None:
    """The tails with something already past its limit."""
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        reports = grounded(unit, day)
    if not reports:
        click.echo(say_nothing_found("grounded"))
        return
    rows: list[tuple[str, ...]] = [("tail", "over", "due", "rate")]
    rows.extend(
        (
            one.serial.number,
            str(len(one.overrun)),
            str(len(one.items)),
            "measured" if one.measured_rate else "planning",
        )
        for one in reports
    )
    click.echo(columns(rows))


@report.command("open")
@click.option("--tail", "serial", default=None)
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.option("--recent", type=int, default=30, show_default=True, help="the recent window in days")
@click.pass_context
def report_open(context: click.Context, serial: str | None, on_day: str, recent: int) -> None:
    """How much damage is open, by kind and by chapter."""
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        if serial:
            click.echo(columns(open_rows(open_on_tail(unit, Serial(serial), day, recent))))
            return
        fleet = open_across_fleet(unit, day, recent)
    rows: list[tuple[str, ...]] = [("tail", "open", "recent", "worst kind")]
    rows.extend(
        (
            one.serial.number,
            str(one.total),
            str(one.raised_recently),
            str(one.worst_kind) if one.worst_kind else "none",
        )
        for one in fleet.tails
    )
    rows.append(("", "", "", ""))
    rows.append(("fleet", str(fleet.total), "", ""))
    click.echo(columns(rows))


def open_rows(counted) -> list[tuple[str, str]]:
    """One tail's open count laid out as a short block."""
    rows = [
        ("tail", counted.serial.number),
        ("as of", counted.on.isoformat()),
        ("open", str(counted.total)),
        ("raised recently", str(counted.raised_recently)),
    ]
    rows.extend((f"  {kind}", str(count)) for kind, count in counted.by_kind)
    rows.extend((f"  chapter {chapter}", str(count)) for chapter, count in counted.by_chapter)
    return rows


def today_or(day: str | None) -> date:
    """A day off the command line, or the real one when it is left off."""
    return as_date(day) if day else date.today()
