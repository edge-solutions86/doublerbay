"""Who is allowed to do what, keyed on the role rather than on the person.

Four roles and they map onto four real jobs. A reader looks at things. A
mechanic raises damage cards and posts counter readings, which is what happens
on the line at three in the morning. An engineer assesses, defers and approves
repairs. The chief engineer does everything an engineer does and also grants
the deferrals that go past the ordinary limits.

The important line is between raising a card and dispositioning it. The person
who found the damage is not the person who decides what happens to it, and a
system that lets one account do both has removed the only check the process
has.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from fastapi import Header, Request

from doublerbay.errors import BadReading, NotOnFile, Refused


class Action(Enum):
    """What somebody is trying to do."""

    READ = "read"
    RAISE_CARD = "raise a damage card"
    POST_READING = "post a counter reading"
    DISPOSITION = "disposition a card"
    DEFER = "grant a deferral"
    AMEND_FLEET = "amend the register"

    def __str__(self) -> str:
        return self.value


class Role(Enum):
    """The four jobs on the desk."""

    READER = "reader"
    MECHANIC = "mechanic"
    ENGINEER = "engineer"
    CHIEF = "chief engineer"

    def can(self, action: Action) -> bool:
        return action in ALLOWED[self]

    def __str__(self) -> str:
        return self.value


ALLOWED: dict[Role, frozenset[Action]] = {
    Role.READER: frozenset({Action.READ}),
    Role.MECHANIC: frozenset({Action.READ, Action.RAISE_CARD, Action.POST_READING}),
    Role.ENGINEER: frozenset(
        {Action.READ, Action.RAISE_CARD, Action.POST_READING, Action.DISPOSITION}
    ),
    Role.CHIEF: frozenset(Action),
}


@dataclass(frozen=True, slots=True)
class ApiKey:
    """One issued key, and who is behind it."""

    key: str
    holder: str
    role: Role

    def __post_init__(self) -> None:
        if len(self.key) < 8:
            raise BadReading("an api key is at least eight characters")
        if not self.holder.strip():
            raise BadReading("a key belongs to somebody")

    def __str__(self) -> str:
        return f"{self.holder} ({self.role})"


class KeyRing:
    """The keys the service will accept."""

    def __init__(self) -> None:
        self._keys: dict[str, ApiKey] = {}

    def issue(self, key: ApiKey) -> None:
        if key.key in self._keys:
            raise BadReading(f"{key.key[:4]} has already been issued")
        self._keys[key.key] = key

    def revoke(self, key: str) -> None:
        if key not in self._keys:
            raise NotOnFile("that key was never issued")
        del self._keys[key]

    def holder_of(self, key: str) -> ApiKey:
        try:
            return self._keys[key]
        except KeyError:
            raise NotOnFile("that key is not one this service accepts") from None

    def __len__(self) -> int:
        return len(self._keys)


def check(ring: KeyRing, key: str | None, action: Action) -> ApiKey:
    """Whether this key may do this, refusing with the reason if not."""
    if not key:
        raise Refused("no api key", "every request says who it is from")
    holder = ring.holder_of(key)
    if not holder.role.can(action):
        raise Refused("not allowed", f"a {holder.role} may not {action}")
    return holder


def require(action: Action):
    """A dependency that lets a request through only if its key may do this."""

    async def guard(request: Request, x_api_key: str | None = Header(default=None)) -> ApiKey:
        ring = getattr(request.app.state, "keys", None)
        if ring is None:
            raise Refused("no key ring", "this service has no keys configured")
        return check(ring, x_api_key, action)

    return guard


def open_ring() -> KeyRing:
    """A ring with one key of each role on it, for a development run."""
    ring = KeyRing()
    for role in Role:
        ring.issue(ApiKey(f"{role.name.lower()}-key-0001", f"the {role} on duty", role))
    return ring
