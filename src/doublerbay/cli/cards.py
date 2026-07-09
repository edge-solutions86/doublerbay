"""The card commands, which take a document rather than fifteen options.

A damage card carries a location on one of two structures and a measurement
set whose fields depend on the kind. Expressed as command line options that is
somewhere north of twenty flags, most of them meaningless for any given kind,
and a mechanic gets three of them wrong at three in the morning.

So a card is raised from a small json document. That is also what the
maintenance system already emits, and what the desk can keep a copy of. The
template command prints a blank one for a kind, with every field the kind
needs and nothing it does not, which is the part people actually get wrong.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import click

from doublerbay.api.schemas import FindingIn, measurements_in, measurements_out
from doublerbay.cli.output import as_date, columns
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.report.history import story_of
from doublerbay.service.intake import next_reference, take_in
from doublerbay.service.measuring import re_measure
from doublerbay.store.unit import UnitOfWork

# What each kind measures. The values are the shape of the field, not a
# default, because a blank a person forgets to fill in should not parse.
MEASUREMENTS: dict[DamageKind, dict[str, Any]] = {
    DamageKind.DENT: {
        "depth": "0.000",
        "along": "0.000",
        "around": "0.000",
        "sharp_bottom": False,
        "cracked": False,
    },
    DamageKind.SCRATCH: {
        "depth": "0.000",
        "length": "0.000",
        "width": "0.000",
        "bearing": "0.0",
        "sharp_bottom": False,
    },
    DamageKind.CRACK: {
        "length": "0.000",
        "bearing": "0.0",
        "through_thickness": False,
        "stop_drilled": False,
        "tips_found": 2,
    },
    DamageKind.CORROSION: {
        "along": "0.000",
        "around": "0.000",
        "worst_depth": "0.000",
        "pitted": False,
        "exfoliating": False,
    },
}
MEASUREMENTS[DamageKind.GOUGE] = MEASUREMENTS[DamageKind.SCRATCH]
MEASUREMENTS[DamageKind.NICK] = MEASUREMENTS[DamageKind.SCRATCH]

FUSELAGE_BLANK = {
    "structure": "fuselage",
    "frame": 0,
    "aft_of_frame": "0.000",
    "stringer": 0,
    "below_stringer": "0.000",
    "side": "L",
}
WING_BLANK = {
    "structure": "wing",
    "rib": 0,
    "outboard_of_rib": "0.000",
    "spar": "front",
    "aft_of_spar": "0.000",
    "surface": "lower",
    "side": "L",
}


def template_for(kind: DamageKind, structure: str) -> dict[str, Any]:
    """A blank card for one kind on one structure."""
    if kind not in MEASUREMENTS:
        raise BadReading(f"there is no card template for a {kind} yet")
    if structure not in ("fuselage", "wing"):
        raise BadReading(f"{structure!r} is not a structure this desk holds")
    blank: dict[str, Any] = {
        "serial": "",
        "kind": kind.value,
        "origin": "unknown",
        "source": "walkaround",
        "found_on": "",
        "at_usage": {"cycles": 0, "hours": "0.0"},
        structure: dict(FUSELAGE_BLANK if structure == "fuselage" else WING_BLANK),
        "measurements": dict(MEASUREMENTS[kind]),
        "note": "",
    }
    return blank


def read_document(path: Path) -> FindingIn:
    """Read a card document off disk, refusing an unreadable one by name.

    The encoding is named rather than left to the platform. A default is
    cp1252 on Windows and utf-8 on Linux, so a card carrying a degree sign or
    an engineer's accented name reads as one thing on the desk and another on
    the server, and nothing tells anybody which one went into the database.
    """
    try:
        loaded = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as broken:
        raise BadReading(f"{path} is not readable json: {broken.msg}") from None
    if not isinstance(loaded, dict):
        raise BadReading(f"{path} is not a card, it is a {type(loaded).__name__}")
    return FindingIn(**loaded)


@click.group()
def cards() -> None:
    """Damage cards: raise them, read them, measure them again."""


@cards.command("template")
@click.argument("kind")
@click.option("--structure", default="fuselage", show_default=True)
def cards_template(kind: str, structure: str) -> None:
    """Print a blank card document for a kind."""
    click.echo(json.dumps(template_for(DamageKind(kind), structure), indent=2))


@cards.command("raise")
@click.argument("document", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.pass_context
def cards_raise(context: click.Context, document: Path) -> None:
    """Raise a card from a document, allocating its reference here."""
    body = read_document(document)
    with UnitOfWork(context.obj["engine"]) as unit:
        reference = next_reference(unit, body.found_on)
        taken = take_in(unit, v320_book(), TypeCatalogue(), body.to_domain(reference), body.urgent)
    click.echo(str(taken))
    by = taken.report_by
    if by is not None:
        click.echo(f"report it by {by.isoformat()}")


@cards.command("show")
@click.argument("reference")
@click.pass_context
def cards_show(context: click.Context, reference: str) -> None:
    """One card, with its measurements as they were written."""
    with UnitOfWork(context.obj["engine"]) as unit:
        card = unit.findings.latest(FindingId.parse(reference))
        rows = [
            ("reference", str(card.reference)),
            ("tail", card.serial.number),
            ("kind", str(card.kind)),
            ("origin", card.origin.value),
            ("found by", card.source.value),
            ("found on", card.found_on.isoformat()),
            ("revision", str(card.revision)),
            ("at", str(card.at_usage)),
            ("where", str(card.point)),
        ]
        rows.extend(
            (name, str(value)) for name, value in sorted(measurements_out(card.geometry).items())
        )
        if card.superseded_by:
            rows.append(("superseded by", str(card.superseded_by)))
        if card.note:
            rows.append(("note", card.note))
    click.echo(columns(rows))


@cards.command("history")
@click.argument("reference")
@click.pass_context
def cards_history(context: click.Context, reference: str) -> None:
    """Every revision of one card, oldest first."""
    with UnitOfWork(context.obj["engine"]) as unit:
        revisions = unit.findings.history(FindingId.parse(reference))
        rows = [("revision", "found on", "at", "measurements")]
        rows.extend(
            (
                str(one.revision),
                one.found_on.isoformat(),
                str(one.at_usage.cycles),
                str(one.geometry),
            )
            for one in revisions
        )
    click.echo(columns(rows))


@cards.command("list")
@click.option("--tail", "serial", required=True)
@click.option("--open", "open_only", is_flag=True, help="only the ones the desk still owns")
@click.pass_context
def cards_list(context: click.Context, serial: str, open_only: bool) -> None:
    """Every card on one tail."""
    with UnitOfWork(context.obj["engine"]) as unit:
        held = Serial(serial)
        found = unit.findings.open_on(held) if open_only else unit.findings.on_tail(held)
        rows = [("reference", "kind", "found on", "where")]
        rows.extend(
            (str(one.reference), str(one.kind), one.found_on.isoformat(), str(one.point))
            for one in found
        )
    click.echo(columns(rows))


@cards.command("measure")
@click.argument("reference")
@click.argument("document", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--on", "on_day", required=True, help="the day it was looked at")
@click.pass_context
def cards_measure(context: click.Context, reference: str, document: Path, on_day: str) -> None:
    """Record a re measurement from a document of the new numbers.

    Only the measurements block is read. The rest of a card document is where
    the damage is and what caused it, and neither of those changes because
    somebody put a depth gauge on it again.
    """
    held = FindingId.parse(reference)
    sent = json.loads(document.read_text(encoding="utf-8"))
    if not isinstance(sent, dict) or "measurements" not in sent:
        raise BadReading(f"{document} carries no measurements block")
    with UnitOfWork(context.obj["engine"]) as unit:
        current = unit.findings.latest(held)
        answer = re_measure(
            unit,
            v320_book(),
            TypeCatalogue(),
            held,
            measurements_in(current.kind, dict(sent["measurements"])),
            as_date(on_day),
        )
    click.echo(str(answer))
    if answer.outgrew_its_deferral:
        click.echo("this is still deferred and the answer got worse, tell an engineer")


@cards.command("story")
@click.argument("reference")
@click.pass_context
def cards_story(context: click.Context, reference: str) -> None:
    """Everything that happened to one card, oldest first."""
    with UnitOfWork(context.obj["engine"]) as unit:
        built = story_of(unit, FindingId.parse(reference))
    click.echo(str(built))
    click.echo("")
    click.echo(columns([(one.on.isoformat(), one.register, one.what) for one in built.entries]))
