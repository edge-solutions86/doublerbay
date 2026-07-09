"""Questions to the manufacturer, from the terminal.

The question itself comes off a file rather than an option. It runs to a
paragraph, it contains the measurements, and getting a paragraph through a
shell without something eating a quote is a losing game. So it is written in
an editor and handed over as a path.

The chase list is what this exists for. An order sitting unanswered is a card
the desk cannot close, and unlike a deferral there is no limit counting down
to make it visible. Somebody has to go and look, so it is one command.
"""

from __future__ import annotations

from pathlib import Path

import click

from doublerbay.cli.output import as_date, columns, say_nothing_found
from doublerbay.disposition.engineering import OrderId, RepairApproval
from doublerbay.errors import BadReading
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory
from doublerbay.service.orders import (
    acknowledge,
    approve,
    raise_order,
    reject,
    send,
    withdraw,
)
from doublerbay.store.unit import UnitOfWork

CATEGORIES = tuple(one.name for one in RepairCategory)


def category_named(text: str) -> RepairCategory:
    """Read a category off the letter the sheet carries rather than the sentence."""
    try:
        return RepairCategory[text.strip().upper()]
    except KeyError:
        raise click.BadParameter(f"{text!r} is not one of {', '.join(CATEGORIES)}") from None


def read_question(path: Path) -> str:
    """Read the question off a file, refusing an empty one.

    Named encoding, for the same reason the card document names one. A
    question to the manufacturer quotes measurements, and a degree sign that
    survives on one platform and not the other is a letter that goes out
    wrong.
    """
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        raise BadReading(f"{path} has no question in it")
    return text


def conditions_from(path: Path | None) -> tuple[str, ...]:
    """One condition per line, blank lines dropped."""
    if path is None:
        return ()
    written = path.read_text(encoding="utf-8")
    return tuple(one.strip() for one in written.splitlines() if one.strip())


@click.group()
def order() -> None:
    """Engineering orders: raise one, chase it, record the answer."""


@order.command("raise")
@click.argument("reference")
@click.option("--question", "path", required=True, type=click.Path(exists=True, path_type=Path))
@click.option("--on", "on_day", required=True, help="yyyy-mm-dd")
@click.option("--by", required=True)
@click.pass_context
def order_raise(context: click.Context, reference: str, path: Path, on_day: str, by: str) -> None:
    """Send a card out to the manufacturer and hold it."""
    with UnitOfWork(context.obj["engine"]) as unit:
        raised = raise_order(
            unit, FindingId.parse(reference), read_question(path), as_date(on_day), by
        )
    click.echo(str(raised))


@order.command("sent")
@click.argument("reference")
@click.option("--on", "on_day", required=True)
@click.pass_context
def order_sent(context: click.Context, reference: str, on_day: str) -> None:
    """Record the day it actually went out."""
    with UnitOfWork(context.obj["engine"]) as unit:
        answer = send(unit, OrderId.parse(reference), as_date(on_day))
    click.echo(f"{answer.reference} {answer.state}")


@order.command("acknowledged")
@click.argument("reference")
@click.pass_context
def order_acknowledged(context: click.Context, reference: str) -> None:
    """Record that the manufacturer has it."""
    with UnitOfWork(context.obj["engine"]) as unit:
        answer = acknowledge(unit, OrderId.parse(reference))
    click.echo(f"{answer.reference} {answer.state}")


@order.command("approved")
@click.argument("reference")
@click.option("--sheet", required=True, help="the repair approval sheet number")
@click.option("--category", required=True, help=f"one of {', '.join(CATEGORIES)}")
@click.option("--on", "on_day", required=True)
@click.option("--by", required=True)
@click.option("--valid-until", default=None, help="when the approval runs out")
@click.option(
    "--conditions",
    "path",
    default=None,
    type=click.Path(exists=True, path_type=Path),
    help="one condition per line",
)
@click.pass_context
def order_approved(
    context: click.Context,
    reference: str,
    sheet: str,
    category: str,
    on_day: str,
    by: str,
    valid_until: str | None,
    path: Path | None,
) -> None:
    """Record an approval and put the card back where a repair can be raised."""
    granted = RepairApproval(
        sheet=sheet,
        category=category_named(category),
        conditions=conditions_from(path),
        valid_until=as_date(valid_until) if valid_until else None,
    )
    with UnitOfWork(context.obj["engine"]) as unit:
        answer = approve(unit, OrderId.parse(reference), granted, as_date(on_day), by)
    click.echo(f"{answer.reference} approved on {granted.sheet}")


@order.command("rejected")
@click.argument("reference")
@click.option("--on", "on_day", required=True)
@click.option("--by", required=True)
@click.pass_context
def order_rejected(context: click.Context, reference: str, on_day: str, by: str) -> None:
    """Record a rejection, which still releases the card."""
    with UnitOfWork(context.obj["engine"]) as unit:
        answer = reject(unit, OrderId.parse(reference), as_date(on_day), by)
    click.echo(f"{answer.reference} {answer.state}")


@order.command("withdraw")
@click.argument("reference")
@click.option("--on", "on_day", required=True)
@click.option("--by", required=True)
@click.pass_context
def order_withdraw(context: click.Context, reference: str, on_day: str, by: str) -> None:
    """Take a question back."""
    with UnitOfWork(context.obj["engine"]) as unit:
        answer = withdraw(unit, OrderId.parse(reference), as_date(on_day), by)
    click.echo(f"{answer.reference} {answer.state}")


@order.command("list")
@click.pass_context
def order_list(context: click.Context) -> None:
    """Everything still waiting on an answer, oldest first."""
    with UnitOfWork(context.obj["engine"]) as unit:
        out = unit.orders.open_orders()
    if not out:
        click.echo(say_nothing_found("out to the manufacturer"))
        return
    rows: list[tuple[str, ...]] = [("reference", "card", "raised", "state", "sent")]
    rows.extend(
        (
            str(one.reference),
            str(one.against),
            one.raised.isoformat(),
            one.state.value,
            one.sent.isoformat() if one.sent else "",
        )
        for one in out
    )
    click.echo(columns(rows))


@order.command("chase")
@click.option("--on", "on_day", required=True, help="the day to measure the wait against")
@click.pass_context
def order_chase(context: click.Context, on_day: str) -> None:
    """The orders that have been out too long, longest first."""
    day = as_date(on_day)
    with UnitOfWork(context.obj["engine"]) as unit:
        waiting = unit.orders.needing_chase(day)
    if not waiting:
        click.echo(say_nothing_found("waiting too long"))
        return
    rows: list[tuple[str, ...]] = [("reference", "card", "sent", "days waiting")]
    rows.extend(
        (
            str(one.reference),
            str(one.against),
            one.sent.isoformat() if one.sent else "not sent",
            str(one.days_waiting(day)),
        )
        for one in waiting
    )
    click.echo(columns(rows))


@order.command("show")
@click.argument("reference")
@click.pass_context
def order_show(context: click.Context, reference: str) -> None:
    """One order and whatever came back."""
    with UnitOfWork(context.obj["engine"]) as unit:
        one = unit.orders.get(OrderId.parse(reference))
    rows = [
        ("reference", str(one.reference)),
        ("card", str(one.against)),
        ("raised", one.raised.isoformat()),
        ("state", one.state.value),
        ("sent", one.sent.isoformat() if one.sent else "not yet"),
        ("answered", one.answered.isoformat() if one.answered else "not yet"),
        ("question", one.question),
    ]
    approval = one.approval
    if approval is not None:
        rows.append(("sheet", approval.sheet))
        rows.append(("category", approval.category.value))
        rows.append(
            ("valid until", approval.valid_until.isoformat() if approval.valid_until else "no end")
        )
        rows.extend(("  condition", text) for text in approval.conditions)
    click.echo(columns(rows))
