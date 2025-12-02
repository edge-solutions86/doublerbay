"""The command line, which is what the desk actually uses at half past six.

The service has an http surface and nobody on the structures desk is going to
open a browser to find out which deferrals expire this week. They are going to
type one line into a terminal on the way to a meeting, so the commands are
short, the output is plain columns, and everything that takes a date takes it
in the same format.

Every command opens its own unit of work and closes it. A long running session
holding a transaction open across a coffee break is how a maintenance system
ends up with a locked row nobody can explain.
"""

from __future__ import annotations

import click

from doublerbay.cli.assessing import assess_card, assess_tail
from doublerbay.cli.cards import cards
from doublerbay.cli.desk import defer, report
from doublerbay.cli.output import as_date, columns
from doublerbay.errors import DoublerbayError
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.forecast import Forecast
from doublerbay.fleet.model import Series
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import Registration, Serial
from doublerbay.measure.usage import Cycles, Hours, Usage
from doublerbay.store.base import IN_MEMORY, build_engine, build_schema, table_names
from doublerbay.store.unit import UnitOfWork


@click.group()
@click.option("--database", default=IN_MEMORY, envvar="DOUBLERBAY_DATABASE", show_default=True)
@click.pass_context
def cli(context: click.Context, database: str) -> None:
    """Skin damage disposition for the structures desk."""
    context.ensure_object(dict)
    # An embedding caller may hand an engine in, which is how the tests run
    # every command against one in memory database rather than a new empty one
    # per invocation.
    context.obj.setdefault("engine", build_engine(database))


@cli.command()
@click.pass_context
def init(context: click.Context) -> None:
    """Build the schema in an empty database."""
    build_schema(context.obj["engine"])
    click.echo(f"built {len(table_names())} tables")


@cli.group()
def fleet() -> None:
    """The register: tails, their counters and what they are flying at."""


@fleet.command("list")
@click.option("--on", "on_day", default=None, help="only what was in service on this day")
@click.pass_context
def fleet_list(context: click.Context, on_day: str | None) -> None:
    """Every tail on the register."""
    with UnitOfWork(context.obj["engine"]) as unit:
        held = unit.aircraft.in_service_on(as_date(on_day)) if on_day else unit.aircraft.all()
        rows = [("serial", "marks", "series", "delivered")]
        rows.extend(
            (one.serial.number, str(one.registration), str(one.series), one.delivered.isoformat())
            for one in held
        )
    click.echo(columns(rows))


@fleet.command("show")
@click.argument("serial")
@click.pass_context
def fleet_show(context: click.Context, serial: str) -> None:
    """One tail, with its latest reading and what it is flying at."""
    with UnitOfWork(context.obj["engine"]) as unit:
        held = Serial(serial)
        aircraft = unit.aircraft.get(held)
        log = unit.aircraft.log_for(held)
        rows = [
            ("tail", str(aircraft)),
            ("line number", str(aircraft.line_number)),
            ("delivered", aircraft.delivered.isoformat()),
            ("last read", log.latest.taken.isoformat()),
            ("flown", str(log.latest.usage)),
        ]
        try:
            forecast = Forecast.from_log(log)
            rows.append(
                ("flying at", f"{forecast.cycles_a_day} FC and {forecast.hours_a_day} FH a day")
            )
        except DoublerbayError:
            rows.append(("flying at", "not enough readings to say"))
    click.echo(columns(rows))


@fleet.command("enrol")
@click.argument("serial")
@click.argument("marks")
@click.option("--series", default="200", show_default=True)
@click.option("--line-number", type=int, required=True)
@click.option("--delivered", required=True, help="yyyy-mm-dd")
@click.pass_context
def fleet_enrol(
    context: click.Context, serial: str, marks: str, series: str, line_number: int, delivered: str
) -> None:
    """Put a tail on the register and open its log at delivery."""
    aircraft = Aircraft(
        serial=Serial(serial),
        registration=Registration.parse(marks),
        series=Series(series),
        line_number=line_number,
        delivered=as_date(delivered),
    )
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.aircraft.add(aircraft)
        unit.aircraft.open_log(UtilisationLog.opened(aircraft.serial, aircraft.delivered))
    click.echo(f"enrolled {aircraft}")


@fleet.command("post")
@click.argument("serial")
@click.argument("taken")
@click.option("--cycles", type=int, required=True)
@click.option("--hours", required=True, help="decimal flight hours")
@click.pass_context
def fleet_post(context: click.Context, serial: str, taken: str, cycles: int, hours: str) -> None:
    """Post a counter reading against a tail."""
    reading = Snapshot(as_date(taken), Usage(Cycles(cycles), Hours.of_hours(hours)))
    with UnitOfWork(context.obj["engine"]) as unit:
        unit.aircraft.post_reading(Serial(serial), reading)
    click.echo(f"posted {reading}")


cli.add_command(cards)
cli.add_command(assess_card)
cli.add_command(assess_tail)
cli.add_command(defer)
cli.add_command(report)


def run() -> None:
    """The entry point, turning a refusal into a message rather than a traceback."""
    try:
        cli(obj={}, standalone_mode=False)
    except DoublerbayError as refusal:
        raise SystemExit(f"refused: {refusal}") from None
    except click.ClickException as problem:
        problem.show()
        raise SystemExit(problem.exit_code) from None
    except click.exceptions.Abort:
        raise SystemExit(1) from None
