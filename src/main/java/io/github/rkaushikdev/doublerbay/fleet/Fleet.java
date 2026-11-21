package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The register: every tail the operator holds, with its log, and the one place that knows which
 * tails there are.
 *
 * <p>Every other question the desk asks starts here: which aeroplanes are in service today, which
 * of them a manual page applies to, what a given tail has flown, and when a limit on it is going to
 * land. Holding that in one object rather than passing four maps around is the difference between a
 * fleet report that agrees with itself and one that does not.
 *
 * <p>Registrations are looked up too, because that is what people type, but they are looked up
 * against the tails currently wearing them. A registration that has moved on answers for the
 * aeroplane wearing it now, and the old repairs stay filed under the serial where they belong.
 *
 * <p>Tails come back in serial order.
 */
public final class Fleet implements Iterable<Aircraft> {

    private final Map<Serial, Aircraft> tails = new LinkedHashMap<>();
    private final Map<Serial, UtilisationLog> logs = new LinkedHashMap<>();

    /** An empty register. */
    public Fleet() {}

    /** Put a tail on the register, opening a log for it at delivery. */
    public void enrol(Aircraft aircraft) {
        enrol(aircraft, UtilisationLog.opened(aircraft.serial(), aircraft.delivered()));
    }

    /** Put a tail on the register with the log it already has. */
    public void enrol(Aircraft aircraft, UtilisationLog log) {
        if (tails.containsKey(aircraft.serial())) {
            throw new BadReading(aircraft.serial() + " is already on the register");
        }
        if (!log.serial().equals(aircraft.serial())) {
            throw new BadReading(log.serial() + " is not the log for " + aircraft.serial());
        }
        tails.put(aircraft.serial(), aircraft);
        logs.put(aircraft.serial(), log);
    }

    /** Replace a tail record after a re registration or a bulletin going on. */
    public void amend(Aircraft aircraft) {
        if (!tails.containsKey(aircraft.serial())) {
            throw new NotOnFile(aircraft.serial() + " is not on the register");
        }
        tails.put(aircraft.serial(), aircraft);
    }

    /** The tail with this serial. */
    public Aircraft tail(Serial serial) {
        Aircraft held = tails.get(serial);
        if (held == null) {
            throw new NotOnFile(serial + " is not on the register");
        }
        return held;
    }

    /** The log of the tail with this serial. */
    public UtilisationLog logFor(Serial serial) {
        UtilisationLog held = logs.get(serial);
        if (held == null) {
            throw new NotOnFile(serial + " has no log on the register");
        }
        return held;
    }

    /** Post a counter reading against a tail. */
    public void post(Serial serial, Snapshot snapshot) {
        logs.put(serial, logFor(serial).posted(snapshot));
    }

    /** Whichever aeroplane is wearing these marks now. */
    public Aircraft byRegistration(Registration marks) {
        for (Aircraft aircraft : tails.values()) {
            if (aircraft.registration().equals(marks)) {
                return aircraft;
            }
        }
        throw new NotOnFile(marks + " is not on the register");
    }

    /** The tails in service on a day. */
    public List<Aircraft> inServiceOn(LocalDate day) {
        return sorted().stream().filter(aircraft -> aircraft.inServiceOn(day)).toList();
    }

    /** Which tails a manual page written for a range actually applies to. */
    public List<Aircraft> coveredBy(Effectivity effectivity) {
        return sorted().stream()
                .filter(aircraft -> aircraft.coveredBy(effectivity))
                .toList();
    }

    /** A forecast off that tail's own log, at its rate over the last year. */
    public Forecast forecastFor(Serial serial) {
        return forecastFor(serial, UtilisationLog.RATE_WINDOW_DAYS);
    }

    /** A forecast off that tail's own log, at its rate over the last stretch of this many days. */
    public Forecast forecastFor(Serial serial, int over) {
        return Forecast.fromLog(logFor(serial), over);
    }

    /** What one tail's counters read on a day. */
    public Usage usageOn(LocalDate day, Serial serial) {
        return logFor(serial).at(day);
    }

    /** Every tail that had been delivered by that day, added up. */
    public Usage fleetUsageOn(LocalDate day) {
        Usage total = Usage.zero();
        for (Map.Entry<Serial, UtilisationLog> held : logs.entrySet()) {
            if (!tails.get(held.getKey()).delivered().isAfter(day)) {
                total = total.plus(held.getValue().at(day));
            }
        }
        return total;
    }

    /** The first delivered of the tails in service on a day; the lower serial when two arrived together. */
    public Aircraft oldestOn(LocalDate day) {
        List<Aircraft> inService = inServiceOn(day);
        if (inService.isEmpty()) {
            throw new NotOnFile("nothing was in service on " + day);
        }
        Aircraft oldest = inService.get(0);
        for (Aircraft aircraft : inService) {
            if (aircraft.delivered().isBefore(oldest.delivered())) {
                oldest = aircraft;
            }
        }
        return oldest;
    }

    /** Every tail, in serial order. */
    @Override
    public Iterator<Aircraft> iterator() {
        return sorted().iterator();
    }

    /** How many tails are on the register. */
    public int size() {
        return tails.size();
    }

    /** Whether a tail with this serial is on the register. */
    public boolean contains(Serial serial) {
        return tails.containsKey(serial);
    }

    private List<Aircraft> sorted() {
        List<Aircraft> held = new ArrayList<>(tails.values());
        held.sort(Comparator.comparingInt(aircraft -> aircraft.serial().asInt()));
        return List.copyOf(held);
    }

    /** {@code 3 tails on the register}. */
    @Override
    public String toString() {
        return tails.size() + " tails on the register";
    }
}
