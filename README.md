# doublerbay

Skin damage disposition for the structures desk of a narrowbody operator.

Maintenance finds damage on the aircraft skin. This is what decides whether the
structural repair manual already allows it, whether a blend out brings it back
inside the limits, what repair it needs if it does not, when that repair has to
be looked at again, and what has to go back to the manufacturer.

The aircraft is the Verdane V320, a single aisle family in three series.

## Layout

    src/doublerbay/measure    lengths, thickness, angles, the units the desk works in
    tests                     the suite

## Working on it

    pip install -e ".[dev]"
    ruff check . && ruff format --check .
    mypy
    pytest -q
