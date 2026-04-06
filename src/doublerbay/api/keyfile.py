"""Loading the key ring off a file, because the development one is not a ring.

The four keys the development ring issues are in the source of this package.
They are fine for a laptop and they are a published credential anywhere else,
so a service that starts for real reads its keys from a file the deployment
owns and this module is the only thing that reads it.

The file is a list rather than a map. A map keyed on the key itself puts the
secret in the position everything logs first, and a list of objects reads the
same way the issuing spreadsheet does.

Nothing here writes a key file. Issuing a key is an act with a person behind
it, and a service that could mint its own would be a service whose audit trail
starts at a program.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from doublerbay.api.security import ApiKey, KeyRing, Role
from doublerbay.errors import BadReading, NotOnFile

# What one entry in the file has to carry.
FIELDS = ("key", "holder", "role")


def role_named(text: str) -> Role:
    """Read a role off the file, refusing one this service does not have."""
    for role in Role:
        if role.value == text or role.name.lower() == text.strip().lower():
            return role
    every = ", ".join(one.value for one in Role)
    raise BadReading(f"{text!r} is not a role, it is one of {every}")


def entry_to_key(entry: Any, where: int) -> ApiKey:
    """One entry off the file, said to be at a position when it is wrong."""
    if not isinstance(entry, dict):
        raise BadReading(f"entry {where} is a {type(entry).__name__}, not a key")
    missing = [one for one in FIELDS if one not in entry]
    if missing:
        raise BadReading(f"entry {where} is missing {', '.join(missing)}")
    return ApiKey(
        key=str(entry["key"]),
        holder=str(entry["holder"]),
        role=role_named(str(entry["role"])),
    )


def ring_from_text(text: str) -> KeyRing:
    """Build a ring from the text of a key file."""
    try:
        loaded = json.loads(text)
    except json.JSONDecodeError as broken:
        raise BadReading(f"the key file is not readable json: {broken.msg}") from None
    if not isinstance(loaded, list):
        raise BadReading(f"a key file is a list of keys, this is a {type(loaded).__name__}")
    if not loaded:
        raise BadReading("a key file with no keys in it would refuse every request")

    ring = KeyRing()
    for where, entry in enumerate(loaded, start=1):
        ring.issue(entry_to_key(entry, where))
    return ring


def ring_from_file(path: Path) -> KeyRing:
    """Read the key ring the service will accept, refusing a file that is not there."""
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        raise NotOnFile(f"{path} is not a key file this service can read") from None
    except OSError as refused:
        raise NotOnFile(f"{path} could not be read: {refused.strerror}") from None
    return ring_from_text(text)


def roles_on(ring: KeyRing) -> tuple[Role, ...]:
    """Which roles the loaded ring can actually authorise, for a start up line.

    A ring with no chief on it cannot grant a deferral, and finding that out
    at six in the morning when an aeroplane is on the ground is worse than
    finding it out in the start up log.
    """
    return tuple(role for role in Role if any(one.role is role for one in _keys(ring)))


def _keys(ring: KeyRing) -> tuple[ApiKey, ...]:
    return tuple(ring._keys.values())  # noqa: SLF001 - the ring is ours


def missing_roles(ring: KeyRing) -> tuple[Role, ...]:
    """The roles nothing on this ring can act as."""
    held = set(roles_on(ring))
    return tuple(role for role in Role if role not in held)
