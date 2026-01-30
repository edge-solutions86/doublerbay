"""The application, and one shape for everything that goes wrong.

Every refusal this package raises already says which kind of wrong it is, so
the mapping to a status code is a table rather than a decision made per route.
A bad reading off a card is the caller's fault and comes back as unprocessable.
A lookup miss is a not found. A move the lifecycle does not allow is a
conflict, because the record moved under the caller rather than the request
being malformed.

The body is the same shape every time. A client that has to parse a different
error format per endpoint ends up parsing none of them, and the field that
matters is the one naming the kind, because that is what a caller branches on.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy import Engine

from doublerbay.api.security import KeyRing, open_ring
from doublerbay.errors import BadReading, DoublerbayError, NotOnFile, OutOfSequence, Refused
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import Book, v320_book

TITLE = "doublerbay"
VERSION = "0.1.0"

STATUS_FOR: dict[type[DoublerbayError], int] = {
    BadReading: 422,
    Refused: 422,
    NotOnFile: 404,
    OutOfSequence: 409,
}


class Problem(BaseModel):
    """What every failure comes back as."""

    kind: str = Field(description="which sort of refusal it was")
    detail: str = Field(description="what the desk would say out loud")


@dataclass(frozen=True, slots=True)
class Deps:
    """Everything a route needs that is not in the request."""

    engine: Engine
    book: Book
    catalogue: TypeCatalogue
    today: date | None = None
    keys: KeyRing | None = None

    def on(self) -> date:
        """The day a request is answered as of, which tests pin and production does not."""
        return self.today if self.today is not None else date.today()


def status_for(error: DoublerbayError) -> int:
    for kind, status in STATUS_FOR.items():
        if isinstance(error, kind):
            return status
    return 500


def problem_of(error: DoublerbayError) -> Problem:
    return Problem(kind=type(error).__name__, detail=str(error))


def build_app(deps: Deps) -> FastAPI:
    """Build the application with its dependencies handed in rather than imported."""
    app = FastAPI(title=TITLE, version=VERSION)
    app.state.deps = deps
    app.state.keys = deps.keys if deps.keys is not None else open_ring()

    @app.exception_handler(DoublerbayError)
    async def _refused(request: Request, error: DoublerbayError) -> JSONResponse:
        return JSONResponse(status_code=status_for(error), content=problem_of(error).model_dump())

    from doublerbay.api import (
        assessment,
        corrosion,
        decisions,
        findings,
        fleet,
        orders,
        repairs,
        reports,
    )

    app.include_router(findings.router)
    app.include_router(assessment.router)
    app.include_router(fleet.router)
    app.include_router(findings.kinds)
    app.include_router(findings.tails)
    app.include_router(decisions.router)
    app.include_router(reports.router)
    app.include_router(corrosion.router)
    app.include_router(repairs.router)
    app.include_router(orders.router)

    @app.get("/health", tags=["service"])
    async def health() -> dict[str, str]:
        """Whether the service is up and what it is reading."""
        return {
            "service": TITLE,
            "version": VERSION,
            "chapters": ",".join(sorted(str(one) for one in deps.book.chapters())),
        }

    return app


def default_app(engine: Engine, today: date | None = None) -> FastAPI:
    """The application as it runs, with the V320 book on the shelf."""
    return build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=today))
