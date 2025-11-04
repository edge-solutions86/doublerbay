"""Asking the book what happens to a card, from the terminal.

This is the command the desk runs most. It takes a reference and a day and it
prints the disposition with every reason that got it there, passed and failed
both, because an engineer who is going to sign against the answer wants to see
which line of which page it came off.

The day is an argument rather than now for the same reason the http route
takes it. What this was assessed as in March and what it would be assessed as
today are different questions once a revision has landed in between, and the
desk asks both.
"""

from __future__ import annotations

from datetime import date

import click

from doublerbay.assess.assessor import Assessment, assess, worst_disposition
from doublerbay.assess.panel import assess_by_panel
from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.fleet.catalogue import TailContext, TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.store.unit import UnitOfWork

TICK = "ok"
CROSS = "no"


def context_for(
    unit: UnitOfWork, catalogue: TypeCatalogue, serial: Serial, on: date
) -> TailContext:
    """Build the tail context off the repositories, as the routes do."""
    aircraft = unit.aircraft.get(serial)
    return TailContext(
        aircraft=aircraft,
        on=on,
        flown=unit.aircraft.log_for(serial).at(on),
        definition=catalogue.definition_for(aircraft.series),
    )


def verdict_lines(one: Assessment) -> list[tuple[str, str]]:
    """The disposition, where it came from, and every reason under it."""
    rows = [
        ("card", str(one.finding.reference)),
        ("part", str(one.placement.part)),
        ("region", str(one.placement.region)),
        ("thickness", str(one.placement.thickness)),
        ("disposition", one.verdict.disposition.value),
        ("why", one.verdict.why()),
    ]
    if one.verdict.reference is not None:
        rows.append(("page", f"{one.verdict.reference} rev {one.verdict.revision}"))
    for reason in one.verdict.reasons:
        rows.append((f"  {TICK if reason.passed else CROSS} {reason.rule}", reason.detail))
    return rows


@click.command("assess")
@click.argument("reference")
@click.option("--on", "on_day", default=None, help="assess it as the book stood on this day")
@click.pass_context
def assess_card(context: click.Context, reference: str, on_day: str | None) -> None:
    """What happens to one card, and every reason behind it."""
    book = v320_book()
    catalogue = TypeCatalogue()
    with UnitOfWork(context.obj["engine"]) as unit:
        finding = unit.findings.latest(FindingId.parse(reference))
        day = as_date(on_day) if on_day else finding.found_on
        tail = context_for(unit, catalogue, finding.serial, day)
        answer = assess(book, tail, finding)
    click.echo(columns(verdict_lines(answer)))


@click.command("assess-tail")
@click.argument("serial")
@click.option("--on", "on_day", required=True, help="the day to assess against")
@click.pass_context
def assess_tail(context: click.Context, serial: str, on_day: str | None) -> None:
    """Every open card on one tail, panel by panel, worst first."""
    book = v320_book()
    catalogue = TypeCatalogue()
    held = Serial(serial)
    day = as_date(on_day) if on_day else None
    with UnitOfWork(context.obj["engine"]) as unit:
        tail = context_for(unit, catalogue, held, day) if day else None
        cards = unit.findings.open_on(held)
        if tail is None:
            raise click.UsageError("assessing a tail needs a day")
        panels = assess_by_panel(book, tail, cards) if cards else ()

    if not panels:
        click.echo(say_nothing_found(f"open on {serial}"))
        return

    rows = [("panel", "cards", "damages", "disposition")]
    rows.extend(
        (str(panel.part), str(panel.card_count), str(panel.damage_count), panel.disposition.value)
        for panel in panels
    )
    every = tuple(one for panel in panels for one in panel.assessments)
    rows.append(("", "", "", ""))
    rows.append(("whole tail", "", "", worst_disposition(every).value))
    click.echo(columns(rows))
