"""What the service promises, listed so it can be checked rather than assumed.

An http surface grows a route at a time and nobody ever reads all of it. The
questions that matter are the ones nobody asks until an audit: which routes
write, which of those are guarded, and which of them nobody wrote down.

So the surface can list itself. Every route, its method, whether it changes
anything, whether it is guarded, and whether it carries a summary somebody
could read. A test then asserts the properties that have to hold, and a route
added next year without a guard fails that test rather than shipping.

The openapi document comes out of the same place. Writing it to a file is what
lets a client generate against a version of the contract rather than against
whatever the service happened to be running when they pointed a tool at it.
"""

from __future__ import annotations

import json
from dataclasses import dataclass

from fastapi import FastAPI
from fastapi.routing import APIRoute

# The methods that change something. Everything else is a read.
WRITING = frozenset({"POST", "PUT", "PATCH", "DELETE"})
# Routes the service exposes without a key, and why each one is on the list.
UNGUARDED = {
    "/health": "a load balancer has no key",
    "/openapi.json": "the contract is public",
    "/docs": "the contract is public",
    "/redoc": "the contract is public",
    "/docs/oauth2-redirect": "the contract is public",
    "/history/registers": "a fixed list with no fleet data on it",
}


@dataclass(frozen=True, slots=True)
class Endpoint:
    """One route, described the way an audit asks about it."""

    path: str
    method: str
    name: str
    summary: str
    guarded: bool
    tags: tuple[str, ...]

    @property
    def writes(self) -> bool:
        return self.method in WRITING

    @property
    def is_documented(self) -> bool:
        return bool(self.summary.strip())

    @property
    def needs_a_guard(self) -> bool:
        """A write is guarded unless it is on the list of ones that are not."""
        return self.writes and self.path not in UNGUARDED

    def __str__(self) -> str:
        mark = "guarded" if self.guarded else "open"
        return f"{self.method} {self.path} ({mark})"


def _guarded(route: APIRoute) -> bool:
    """Whether anything on this route asks for a key.

    Read off the dependency's own closure rather than a decorator, because the
    guard is built by a factory and every route gets a different function
    object out of it.
    """
    for dependency in route.dependant.dependencies:
        call = getattr(dependency, "call", None)
        if call is not None and getattr(call, "__name__", "") == "guard":
            return True
    return False


def endpoints(app: FastAPI) -> tuple[Endpoint, ...]:
    """Every route the application serves, in path order."""
    found: list[Endpoint] = []
    for route in app.routes:
        if not isinstance(route, APIRoute):
            continue
        for method in sorted(route.methods or ()):
            found.append(
                Endpoint(
                    path=route.path,
                    method=method,
                    name=route.name,
                    summary=(route.description or "").strip().splitlines()[0]
                    if route.description
                    else "",
                    guarded=_guarded(route),
                    tags=tuple(str(one) for one in route.tags),
                )
            )
    found.sort(key=lambda one: (one.path, one.method))
    return tuple(found)


def writes(app: FastAPI) -> tuple[Endpoint, ...]:
    return tuple(one for one in endpoints(app) if one.writes)


def unguarded_writes(app: FastAPI) -> tuple[Endpoint, ...]:
    """Every write nothing is guarding, which should always be empty."""
    return tuple(one for one in endpoints(app) if one.needs_a_guard and not one.guarded)


def undocumented(app: FastAPI) -> tuple[Endpoint, ...]:
    """Every route with nothing written on it."""
    return tuple(one for one in endpoints(app) if not one.is_documented)


def tags(app: FastAPI) -> tuple[str, ...]:
    """Every tag the surface groups its routes under."""
    held: set[str] = set()
    for one in endpoints(app):
        held.update(one.tags)
    return tuple(sorted(held))


def as_document(app: FastAPI) -> str:
    """The openapi contract, formatted so a diff between versions is readable."""
    return json.dumps(app.openapi(), indent=2, sort_keys=True)
