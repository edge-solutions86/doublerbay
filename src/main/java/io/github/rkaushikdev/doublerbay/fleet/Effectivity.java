package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which tails something applies to.
 *
 * <p>A limit is not written for an aeroplane, it is written for a range of them. The manual says a
 * page applies to serials 5001 through 5240, or to everything with service bulletin 53-018
 * embodied, or to everything without it, and the same damage on two tails a year apart in build
 * gets two different answers because of it.
 *
 * <p>Effectivity is therefore a first class thing here and not a comment. It is a serial range,
 * optionally narrowed by a bulletin being embodied or not, and the only question anyone asks of it
 * is whether a given tail is inside it.
 */
public record Effectivity(
        Set<Series> series,
        Optional<Integer> firstSerial,
        Optional<Integer> lastSerial,
        Optional<Bulletin> requires,
        Optional<Bulletin> excludes) {

    /** Effectivity against at least one series, a range that runs forwards, and no contradiction. */
    public Effectivity {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(firstSerial, "firstSerial");
        Objects.requireNonNull(lastSerial, "lastSerial");
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(excludes, "excludes");
        if (series.isEmpty()) {
            throw new BadReading("effectivity against no series applies to nothing");
        }
        series = Collections.unmodifiableSet(EnumSet.copyOf(series));
        if (firstSerial.isPresent() && lastSerial.isPresent() && firstSerial.get() > lastSerial.get()) {
            throw new BadReading("serials " + firstSerial.get() + " to " + lastSerial.get() + " run backwards");
        }
        if (requires.isPresent() && requires.equals(excludes)) {
            throw new BadReading(requires.get() + " cannot be both required and excluded");
        }
    }

    /** Every series, every serial, whatever is embodied. */
    public static Effectivity allAircraft() {
        return of(EnumSet.allOf(Series.class), null, null, null, null);
    }

    /** Effectivity from whichever clauses are given; a null clause is one it does not narrow on. */
    public static Effectivity of(
            Set<Series> series, Integer firstSerial, Integer lastSerial, Bulletin requires, Bulletin excludes) {
        return new Effectivity(
                series,
                Optional.ofNullable(firstSerial),
                Optional.ofNullable(lastSerial),
                Optional.ofNullable(requires),
                Optional.ofNullable(excludes));
    }

    /** The same effectivity, narrowed to these series. */
    public Effectivity withSeries(Set<Series> only) {
        return new Effectivity(only, firstSerial, lastSerial, requires, excludes);
    }

    /** The same effectivity, starting at this serial. */
    public Effectivity withFirstSerial(int first) {
        return new Effectivity(series, Optional.of(first), lastSerial, requires, excludes);
    }

    /** The same effectivity, ending at this serial. */
    public Effectivity withLastSerial(int last) {
        return new Effectivity(series, firstSerial, Optional.of(last), requires, excludes);
    }

    /** The same effectivity over this serial range, both ends included. */
    public Effectivity withSerials(int first, int last) {
        return new Effectivity(series, Optional.of(first), Optional.of(last), requires, excludes);
    }

    /** The same effectivity, only for tails with this bulletin embodied. */
    public Effectivity withRequires(Bulletin bulletin) {
        return new Effectivity(series, firstSerial, lastSerial, Optional.of(bulletin), excludes);
    }

    /** The same effectivity, only for tails without this bulletin embodied. */
    public Effectivity withExcludes(Bulletin bulletin) {
        return new Effectivity(series, firstSerial, lastSerial, requires, Optional.of(bulletin));
    }

    /** Whether one tail, in the configuration it is in today, is inside this. */
    public boolean covers(Series flying, Serial serial, Set<Bulletin> embodied) {
        if (!series.contains(flying)) {
            return false;
        }
        int number = serial.asInt();
        if (firstSerial.isPresent() && number < firstSerial.get()) {
            return false;
        }
        if (lastSerial.isPresent() && number > lastSerial.get()) {
            return false;
        }
        if (requires.isPresent() && !embodied.contains(requires.get())) {
            return false;
        }
        return !(excludes.isPresent() && embodied.contains(excludes.get()));
    }

    /** Whether the range runs on to every serial yet to be built. */
    public boolean isOpenEnded() {
        return lastSerial.isEmpty();
    }

    /** {@code 200; MSN 5100 to 5200; with SB 53-018}, the way the page prints it. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(series.stream().map(Series::value).sorted().collect(Collectors.joining(",")));
        if (firstSerial.isPresent() || lastSerial.isPresent()) {
            String first = firstSerial.map(String::valueOf).orElse("first");
            String last = lastSerial.map(String::valueOf).orElse("on");
            parts.add("MSN " + first + " to " + last);
        }
        requires.ifPresent(bulletin -> parts.add("with " + bulletin));
        excludes.ifPresent(bulletin -> parts.add("without " + bulletin));
        return String.join("; ", parts);
    }
}
