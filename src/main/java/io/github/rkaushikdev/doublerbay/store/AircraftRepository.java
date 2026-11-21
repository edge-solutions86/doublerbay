package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NonUniqueResultException;
import java.time.LocalDate;
import java.util.List;

/**
 * Tails and their logs, keyed on the serial.
 *
 * <p>A repository takes a session and hands back domain objects. Nothing above this layer sees a
 * row, a query or a column name, and every read goes through the domain validation, so a row that
 * got into the database wrong fails on the way out at the point where somebody can be told which
 * record it was.
 */
public final class AircraftRepository {

    private final EntityManager session;

    AircraftRepository(EntityManager session) {
        this.session = session;
    }

    /** Put a tail on the register, refusing one already there. */
    public void add(Aircraft aircraft) {
        if (row(aircraft.serial()) != null) {
            throw new BadReading(aircraft.serial() + " is already on the register");
        }
        session.persist(Mapping.aircraftToRow(aircraft));
    }

    /**
     * Put down a new configuration of a tail already on the register. The tail's row is replaced
     * whole, and what hangs off it goes with the old row.
     */
    public void amend(Aircraft aircraft) {
        AircraftRow held = row(aircraft.serial());
        if (held == null) {
            throw new NotOnFile(aircraft.serial() + " is not on the register");
        }
        session.remove(held);
        session.flush();
        session.persist(Mapping.aircraftToRow(aircraft));
    }

    /** One tail by its serial. */
    public Aircraft get(Serial serial) {
        AircraftRow held = row(serial);
        if (held == null) {
            throw new NotOnFile(serial + " is not on the register");
        }
        return Mapping.rowToAircraft(held);
    }

    /** The tail wearing some marks. */
    public Aircraft byRegistration(Registration marks) {
        List<AircraftRow> rows = session.createQuery(
                        "select a from AircraftRow a where a.registration = :marks", AircraftRow.class)
                .setParameter("marks", marks)
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotOnFile(marks + " is not on the register");
        }
        if (rows.size() > 1) {
            throw new NonUniqueResultException(marks + " is worn by more than one tail on the register");
        }
        return Mapping.rowToAircraft(rows.get(0));
    }

    /** Every tail on the register, in serial order. */
    public List<Aircraft> all() {
        return session
                .createQuery("select a from AircraftRow a order by a.serial", AircraftRow.class)
                .getResultList()
                .stream()
                .map(Mapping::rowToAircraft)
                .toList();
    }

    /** Every tail that was in service on a day. */
    public List<Aircraft> inServiceOn(LocalDate day) {
        return all().stream().filter(one -> one.inServiceOn(day)).toList();
    }

    /** Add one reading, refusing a counter that has gone backwards. */
    public void postReading(Serial serial, Snapshot snapshot) {
        logFor(serial).posted(snapshot);
        session.persist(new ReadingRow(
                serial,
                snapshot.taken(),
                snapshot.usage().cycles(),
                snapshot.usage().hours()));
    }

    /** The whole log of one tail, in the order the readings were taken. */
    public UtilisationLog logFor(Serial serial) {
        List<ReadingRow> rows = session.createQuery(
                        "select r from ReadingRow r where r.serial = :serial order by r.taken", ReadingRow.class)
                .setParameter("serial", serial.number())
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotOnFile(serial + " has no log on the register");
        }
        return Mapping.rowsToLog(serial, rows);
    }

    /** Open a tail's log with the readings it starts with. */
    public void openLog(UtilisationLog log) {
        for (ReadingRow row : Mapping.logToRows(log)) {
            session.persist(row);
        }
    }

    private AircraftRow row(Serial serial) {
        return session.find(AircraftRow.class, serial.number());
    }
}
