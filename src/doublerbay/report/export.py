"""Getting a report out of this service and into the spreadsheet it will end up in.

Every report on this desk ends its life in a spreadsheet. Somebody pastes it
into one, sorts it a different way, adds a column of their own and emails it.
Pretending otherwise means they retype it, and a retyped report is a report
with a transcription error in it.

So the reports come out as csv, with the columns named the way the printed
version names them. The point is that the exported file and the printed one
carry the same numbers, which is why both go through the same row builders.

Quoting is left to the csv module. A part number contains no comma today and
one will the moment somebody adds a variant suffix, and hand rolled joining is
how that becomes a corrupted file six months later.
"""

from __future__ import annotations

import csv
from collections.abc import Iterable, Sequence
from io import StringIO

from doublerbay.errors import BadReading
from doublerbay.report.due import DueItem
from doublerbay.report.growth import Growth
from doublerbay.report.workpack import WorkPack

# Written with the line ending a spreadsheet on either platform reads.
LINE_END = "\r\n"


def as_csv(header: Sequence[str], rows: Iterable[Sequence[str]]) -> str:
    """Write a header and its rows, refusing a row that is the wrong width."""
    if not header:
        raise BadReading("a csv file has a header on it")
    out = StringIO()
    writer = csv.writer(out, lineterminator=LINE_END)
    writer.writerow(header)
    for at, row in enumerate(rows, start=2):
        if len(row) != len(header):
            raise BadReading(f"row {at} has {len(row)} columns and the header has {len(header)}")
        writer.writerow(row)
    return out.getvalue()


DUE_HEADER = ("tail", "card", "what", "due", "days", "clock", "over")


def due_rows(items: tuple[DueItem, ...]) -> list[tuple[str, ...]]:
    return [
        (
            one.serial.number,
            str(one.against),
            one.what,
            str(one.due),
            str(one.margin.days),
            one.margin.clock.value,
            "yes" if one.is_overrun else "no",
        )
        for one in items
    ]


def due_as_csv(items: tuple[DueItem, ...]) -> str:
    """The due list, in the order it was given."""
    return as_csv(DUE_HEADER, due_rows(items))


PACK_HEADER = ("block", "reference", "detail", "over")


def pack_rows(pack: WorkPack) -> list[tuple[str, ...]]:
    """Every line of a work pack, with the block it came from as a column.

    The printed version puts the block in a heading. A spreadsheet cannot sort
    on a heading, so here it becomes a column on every row.
    """
    rows: list[tuple[str, ...]] = []
    for block, lines in (
        ("deferral", pack.deferrals),
        ("inspection", pack.inspections),
        ("waiting", pack.waiting),
    ):
        rows.extend(
            (block, one.reference, one.detail, "yes" if one.overrun else "no") for one in lines
        )
    return rows


def pack_as_csv(pack: WorkPack) -> str:
    """One work pack, flattened so a planner can sort it."""
    return as_csv(PACK_HEADER, pack_rows(pack))


GROWTH_HEADER = ("card", "readings", "total", "worst rate", "trend")


def growth_rows(every: tuple[Growth, ...]) -> list[tuple[str, ...]]:
    return [
        (
            str(one.reference),
            str(one.readings),
            str(one.total.inches),
            str(one.worst_rate) if one.worst_rate is not None else "",
            "yes" if one.is_a_trend else "no",
        )
        for one in every
    ]


def growth_as_csv(every: tuple[Growth, ...]) -> str:
    """Measured crack growth, one card a row."""
    return as_csv(GROWTH_HEADER, growth_rows(every))


def line_count(written: str) -> int:
    """How many rows a written file carries, header not counted."""
    return max(len(written.split(LINE_END)) - 2, 0)
