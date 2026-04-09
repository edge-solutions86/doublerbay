"""Starting the service, and saying what it would start with before it does.

The settings command exists because the failure everybody hits is the same
one: the service starts on a machine where one environment variable is spelled
differently, and the first sign of it is a request failing at three in the
morning. So there is a command that reads the configuration, builds the key
ring, says what it found, and exits without listening on anything.

It also says which roles the ring can act as. A ring with no chief engineer on
it is a service that cannot grant a deferral, and that is worth knowing on the
day it is deployed rather than the day an aeroplane is on the ground.
"""

from __future__ import annotations

import click

from doublerbay.api.app import Deps, build_app
from doublerbay.api.keyfile import missing_roles, ring_from_file, roles_on
from doublerbay.cli.output import columns
from doublerbay.config import Settings, described, from_environment
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, table_names


def application(settings: Settings):
    """Build the application the way a real start up builds it."""
    return build_app(
        Deps(
            engine=build_engine(settings.database),
            book=v320_book(),
            catalogue=TypeCatalogue(),
            today=settings.today,
            keys=ring_from_file(settings.keys),
        )
    )


def settings_rows(settings: Settings) -> list[tuple[str, str]]:
    """What the service was told, laid out for a start up check."""
    ring = ring_from_file(settings.keys)
    short = missing_roles(ring)
    return [
        ("database", settings.database),
        ("stored", "in memory" if settings.is_in_memory else "on disk"),
        ("keys", str(settings.keys)),
        ("keys loaded", str(len(ring))),
        ("roles", ", ".join(str(one) for one in roles_on(ring))),
        ("roles missing", ", ".join(str(one) for one in short) if short else "none"),
        ("listen", f"{settings.host}:{settings.port}"),
        ("recent window", f"{settings.recent_days} days"),
        ("day", settings.today.isoformat() if settings.today else "the real one"),
        ("tables", str(len(table_names()))),
    ]


@click.command("settings")
def show_settings() -> None:
    """Read the configuration, build the key ring, and print what was found."""
    settings = from_environment()
    click.echo(columns(settings_rows(settings)))
    short = missing_roles(ring_from_file(settings.keys))
    if short:
        click.echo("")
        click.echo(f"nothing on this ring can act as: {', '.join(str(one) for one in short)}")


@click.command("variables")
def show_variables() -> None:
    """Every environment variable this service reads."""
    click.echo("\n".join(described()))


@click.command("serve")
@click.option("--host", default=None, help="override the configured host")
@click.option("--port", type=int, default=None, help="override the configured port")
def serve(host: str | None, port: int | None) -> None:
    """Start the http service against the configured database.

    Uvicorn is imported here rather than at the top of the module. Every other
    command in this package works without a web server installed, and a plain
    import would make the command line refuse to start at all when it is not.
    """
    import uvicorn

    settings = from_environment()
    click.echo(str(settings))
    uvicorn.run(
        application(settings),
        host=host or settings.host,
        port=port or settings.port,
        log_level="info",
    )
