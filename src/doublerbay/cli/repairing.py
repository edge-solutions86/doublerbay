"""Drawing and fitting a repair from the terminal.

The shop wants the drawing before it wants anything written down, so the draw
command works out the doubler and prints it without touching the database.
Somebody reads the numbers, argues about the gauge, and only then runs fit.

That split is the whole design here. A command that drew and committed in one
step would mean every question about what a repair would look like left a
repair on the register, and the fleet would accumulate repairs nobody fitted.
"""

from __future__ import annotations

from datetime import date

import click

from doublerbay.assess.placement import Placement, place
from doublerbay.cli.assessing import context_for
from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, RepairId, Serial
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import RepairScheme, select_scheme
from doublerbay.service.repairs import embody, remove
from doublerbay.store.unit import UnitOfWork

DEFAULT_PAGE = "53-40-01"


def as_inches(text: str) -> Length:
    """Read a measurement the way it is written on the sheet."""
    return Length.of_inches(text)


def drawing_rows(scheme: RepairScheme, placement: Placement) -> list[tuple[str, str]]:
    """The drawing laid out the way the shop reads it off the sheet."""
    plan = scheme.doubler
    rows = [
        ("part", str(placement.part)),
        ("region", str(placement.region)),
        ("skin", str(placement.thickness.inches)),
        ("form", scheme.form.value),
        ("category", scheme.category.value),
        ("sealed", "yes" if scheme.sealed else "no"),
        ("page", str(scheme.reference)),
        ("material", str(plan.material)),
        ("gauge", str(plan.gauge.inches)),
        ("along", str(plan.along.inches)),
        ("around", str(plan.around.inches)),
        ("fastener", f"dash {plan.size.dash}"),
        ("fasteners", str(plan.fastener_count)),
        ("rows each side", str(plan.rows_each_side)),
        ("per row", str(plan.per_row)),
        ("pitch", str(plan.pitch.inches)),
        ("row spacing", str(plan.row_spacing.inches)),
        ("edge distance", str(plan.edge_distance.inches)),
    ]
    if scheme.filler is not None:
        rows.append(("filler", str(scheme.filler.inches)))
    return rows


def drawn_for(
    unit: UnitOfWork,
    reference: FindingId,
    along: str,
    around: str,
    page: str,
    dash: int | None,
    temporary: bool,
    on: date,
) -> tuple[RepairScheme, Placement, Serial]:
    """Work out the repair for a hole against a card, without writing anything."""
    finding = unit.findings.latest(reference)
    tail = context_for(unit, TypeCatalogue(), finding.serial, on)
    placement = place(tail, finding)
    scheme = select_scheme(
        as_inches(along),
        as_inches(around),
        placement,
        Reference.parse(page),
        FastenerSize(dash) if dash else None,
        temporary,
    )
    return scheme, placement, finding.serial


@click.group()
def repair() -> None:
    """Repairs: draw one, fit it, take it off."""


@repair.command("draw")
@click.argument("reference")
@click.option("--along", required=True, help="how long the cut out is, in inches")
@click.option("--around", required=True, help="how wide the cut out is, in inches")
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.option("--page", default=DEFAULT_PAGE, show_default=True)
@click.option("--fastener", "dash", type=int, default=None, help="dash number")
@click.option("--temporary", is_flag=True)
@click.pass_context
def repair_draw(
    context: click.Context,
    reference: str,
    along: str,
    around: str,
    on_day: str,
    page: str,
    dash: int | None,
    temporary: bool,
) -> None:
    """Work out the repair for a hole and print it, writing nothing."""
    with UnitOfWork(context.obj["engine"]) as unit:
        scheme, placement, _ = drawn_for(
            unit, FindingId.parse(reference), along, around, page, dash, temporary, as_date(on_day)
        )
    click.echo(columns(drawing_rows(scheme, placement)))


@repair.command("fit")
@click.argument("reference")
@click.option("--sequence", type=int, required=True, help="the repair number in the chapter")
@click.option("--along", required=True)
@click.option("--around", required=True)
@click.option("--centre-along", required=True, help="body station of the centre")
@click.option("--centre-around", required=True, help="offset around from the centre line")
@click.option("--on", "on_day", required=True, help="the day it went on")
@click.option("--by", required=True)
@click.option("--page", default=DEFAULT_PAGE, show_default=True)
@click.option("--fastener", "dash", type=int, default=None)
@click.option("--temporary", is_flag=True)
@click.pass_context
def repair_fit(
    context: click.Context,
    reference: str,
    sequence: int,
    along: str,
    around: str,
    centre_along: str,
    centre_around: str,
    on_day: str,
    by: str,
    page: str,
    dash: int | None,
    temporary: bool,
) -> None:
    """Fit a repair, close the card behind it and lift its deferral."""
    held = FindingId.parse(reference)
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        scheme, placement, serial = drawn_for(unit, held, along, around, page, dash, temporary, day)
        record = RepairRecord(
            reference=RepairId(placement.chapter.value, sequence),
            part=placement.part,
            against=held,
            scheme=scheme,
            centre=Offset(as_inches(centre_along), as_inches(centre_around)),
            embodied=day,
            at_usage=unit.aircraft.log_for(serial).at(day),
        )
        answer = embody(unit, record, serial, by, day)
    click.echo(str(answer))
    for one, other in answer.clashes:
        click.echo(f"too close: {one} and {other}")


@repair.command("list")
@click.option("--tail", "serial", required=True)
@click.option("--all", "everything", is_flag=True, help="include the ones taken off")
@click.pass_context
def repair_list(context: click.Context, serial: str, everything: bool) -> None:
    """Every repair on one tail."""
    with UnitOfWork(context.obj["engine"]) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        fitted = unit.repairs.on_tail(held, fitted_only=not everything)
    if not fitted:
        click.echo(say_nothing_found(f"fitted to {serial}"))
        return
    rows: list[tuple[str, ...]] = [("reference", "part", "against", "category", "fitted", "off")]
    rows.extend(
        (
            str(one.reference),
            str(one.part),
            str(one.against),
            one.scheme.category.name,
            one.embodied.isoformat(),
            one.removed.isoformat() if one.removed else "",
        )
        for one in fitted
    )
    click.echo(columns(rows))


@repair.command("remove")
@click.argument("chapter", type=int)
@click.argument("sequence", type=int)
@click.option("--on", "on_day", required=True, help="the day it came off")
@click.pass_context
def repair_remove(context: click.Context, chapter: int, sequence: int, on_day: str) -> None:
    """Take a repair off, which does not reopen the card it answered."""
    held = RepairId(chapter, sequence)
    with UnitOfWork(context.obj["engine"]) as unit:
        remove(unit, held, as_date(on_day))
    click.echo(f"removed {held}")
