# doublerbay

[![CI](https://github.com/rkaushik-dev/doublerbay/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/doublerbay/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Gradle](https://img.shields.io/badge/build-Gradle-02303A)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Skin damage disposition for the structures desk of a narrowbody operator.

Maintenance finds damage on the aircraft skin. This is what decides whether the
structural repair manual already allows it, whether a blend out brings it back
inside the limits, what repair it needs if it does not, when that repair has to
be looked at again, and what has to go back to the manufacturer.

The aircraft is the Verdane V320, a single aisle family in three series.

## Tech stack

- Java 21, built with Gradle (Groovy DSL) through the wrapper
- Spring Boot 3.5 (Web MVC, Validation) for the HTTP service, with the OpenAPI
  contract served by springdoc
- Hibernate (JPA) over H2, the schema built by Flyway migrations and checked
  against the mapped rows when the service starts
- picocli for the `doublerbay` command line
- JUnit 5 and AssertJ; Spotless (palantir-java-format) and a JaCoCo line
  coverage gate of 80% run as part of the build

## Building and testing

    ./gradlew build          # compile, format check, every test, coverage gate, runnable jar
    ./gradlew test           # the suite on its own
    ./gradlew spotlessApply  # format the sources

The build writes `build/libs/doublerbay-0.1.0.jar`, which runs the command line:

    java -jar build/libs/doublerbay-0.1.0.jar --help

Lengths are held as whole thousandths of an inch, angles as whole tenths of a
degree, and flight hours as whole minutes, so a limit of exactly 0.040 and a
damage of exactly 0.040 always compare equal. Decimal arithmetic runs at 28
significant digits with ties going to the even neighbour.

## Command line

Every command takes `--database` (or `DOUBLERBAY_DATABASE`), a JDBC URL such as
`jdbc:h2:file:./desk`. Without one it works on a fresh in-memory database.

    doublerbay --database jdbc:h2:file:./desk init        # build the schema
    doublerbay --database jdbc:h2:file:./desk seed        # a demonstration fleet

| command | what it does |
| --- | --- |
| `init` | build the schema in an empty database |
| `seed` | put a small demonstration fleet on an empty database |
| `fleet list / show / enrol / post / import` | the register: tails, their counters and what they are flying at |
| `cards template / raise / list / show / measure / history / story` | damage cards, raised from documents, measured again, and their whole story |
| `assess` / `assess-tail` | what one card, or every open card on a tail, is assessed as |
| `defer grant / show / tighten / lift` | deferrals and their limits |
| `report due / grounded / open / revision` | the due list, the grounded tails, the open damage, and what a manual revision changes |
| `order raise / list / show / sent / acknowledged / approved / rejected / withdraw / chase` | questions out to the manufacturer |
| `repair draw / fit / list / remove` | repair schemes, fitted, listed and taken off |
| `check pack / due / programme / done` | the work pack for a check, the repeat inspections due, and signing one off |
| `corrosion list / programme / unreported / reported` | the corrosion programme |
| `serve` | run the HTTP service |
| `settings` / `variables` | the configuration the service would start with, and every variable it reads |
| `routes` / `contract` | the HTTP surface with its guards, and the OpenAPI contract written to a file |
| `gates` | the checks this repository runs, the same ones the workflow runs |

A refusal prints `refused: <why>` and exits 1; a usage error exits 2.

## HTTP service

    DOUBLERBAY_DATABASE=jdbc:h2:file:./desk DOUBLERBAY_KEYS=keys.json doublerbay serve

The database URL and the key file have no defaults on purpose. Optional
variables: `DOUBLERBAY_HOST` (127.0.0.1), `DOUBLERBAY_PORT` (8000),
`DOUBLERBAY_RECENT_DAYS` (30) and `DOUBLERBAY_TODAY` (a pinned day). Every request
carries its key in `x-api-key`; reads need any role, writes need the role whose
job they are. The contract is at `/openapi.json` and its pages at `/docs`.
Every refusal has one body: `type`, `kind`, `detail`, `where`, `retryable`.

| area | routes |
| --- | --- |
| service | `GET /health` |
| fleet | `GET/POST /aircraft`, `GET /aircraft/{serial}`, `GET /aircraft-by-marks/{marks}`, `POST /aircraft/{serial}/readings`, `GET /aircraft/{serial}/utilisation`, `GET /aircraft/{serial}/rate` |
| findings | `POST /findings`, `GET /findings/{reference}`, `POST /findings/{reference}/revisions`, `GET /findings/{reference}/history`, `GET/POST /findings/{reference}/state`, `POST /findings/{reference}/supersede`, `GET /findings-of-kind/{kind}`, `GET /aircraft/{serial}/findings` |
| assessment | `GET /findings/{reference}/assessment`, `GET /aircraft/{serial}/assessment` |
| deferrals | `GET/POST /deferrals`, `GET /deferrals/{reference}`, `POST /deferrals/{reference}/tighten`, `POST /deferrals/{reference}/lift` |
| reports | `GET /reports/due`, `GET /reports/due/{serial}`, `GET /reports/grounded`, `GET /reports/open`, `GET /reports/open/{serial}` |
| corrosion | `GET /corrosion/aircraft/{serial}`, `GET /corrosion/aircraft/{serial}/programme`, `GET /corrosion/level/{level}`, `GET /corrosion/unreported`, `POST /corrosion/{reference}/reported` |
| repairs | `POST /repairs`, `GET /repairs/{chapter}/{sequence}`, `POST /repairs/{chapter}/{sequence}/remove`, `GET /repairs/aircraft/{serial}` |
| orders | `GET/POST /orders`, `GET /orders/{reference}`, `POST /orders/{reference}/sent`, `/acknowledged`, `/approved`, `/rejected`, `/withdrawn`, `GET /orders/chase` |
| inspections | `GET /inspections/{chapter}/{sequence}`, `POST /inspections/{chapter}/{sequence}/done`, `GET /inspections/aircraft/{serial}`, `/due`, `/never-done` |
| checks | `GET /checks/{serial}/pack` |
| audit | `GET /audit`, `GET /audit/{serial}` |
| growth | `GET /growth`, `GET /growth/{reference}`, `GET /growth/aircraft/{serial}` |
| history | `GET /history/registers`, `GET /history/{reference}` |

The `routes` command lists the same surface with each route's guard, and exits
non zero if anything that writes is unguarded.

## Layout

    src/main/java/io/github/rkaushikdev/doublerbay/
        measure      lengths, thickness, angles, ratios, counters, intervals, fasteners, gauges
        ids          registrations, serials, damage report, repair and panel numbers
        grid         zones, frames, stringers, ribs, spars, stations and points on the skin
        structure    materials, joints, panels, thickness maps, cutouts, the airframe as built
        v320         the V320 barrel, wing box and the transcribed manual chapters
        damage       the measurement sets, findings, clusters, the damage register
        manual       manual revisions, limit tables, clean-up rules, the book on the shelf
        assess       placing a card on the skin and the rules that dispose of it
        repair       doublers, repair schemes, blends, repair records
        tolerance    inspection methods, schedules, the repeat programme, corrosion grading
        disposition  a card's lifecycle, deferrals, engineering orders
        fleet        series, tails, utilisation, checks, forecasts, the type catalogue
        report       the due list, open damage, growth, audits, work packs, csv export
        service      taking cards in, measuring, readings, orders, repairs, seeding
        store        the mapped rows, repositories and the unit of work
        api          the HTTP service
        cli          the command line
        config, logging, complaints, tooling, util
    src/main/resources/db/migration   the Flyway migrations, one per schema revision
    src/test/java                     the suite
