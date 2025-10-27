"""Reading arguments and laying out answers, kept apart from the commands.

Two commands in different modules both want to print a table, and the one that
imports the other to get it creates a cycle the moment the first one grows a
subcommand. So the small shared pieces live here and nothing here imports a
command.

The layout is deliberately plain columns rather than a box drawing library.
The output of these commands gets pasted into an email to a lessor, and a
table made of unicode corners does not survive that trip.
"""

from __future__ import annotations

from collections.abc import Iterable
from datetime import date, datetime

from doublerbay.errors import BadReading

DATE_FORMAT = "%Y-%m-%d"
GAP = "  "


def as_date(text: str) -> date:
    """Read a date the one way, refusing anything else by name."""
    try:
        return datetime.strptime(text, DATE_FORMAT).date()
    except ValueError:
        raise BadReading(f"{text!r} is not a date, write it as yyyy-mm-dd") from None


def columns(rows: Iterable[Iterable[str]]) -> str:
    """Lay rows out in columns wide enough for what is in them."""
    laid = [list(row) for row in rows]
    if not laid:
        return ""
    width = max(len(row) for row in laid)
    padded = [row + [""] * (width - len(row)) for row in laid]
    widths = [max(len(row[index]) for row in padded) for index in range(width)]
    return "\n".join(
        GAP.join(cell.ljust(size) for cell, size in zip(row, widths, strict=True)).rstrip()
        for row in padded
    )


def say_nothing_found(what: str) -> str:
    """What to print when a report has no rows, which is not an error."""
    return f"nothing {what}"
