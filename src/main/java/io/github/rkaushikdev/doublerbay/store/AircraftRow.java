package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One tail, keyed on the serial the airframe keeps for life.
 *
 * <p>The serial is the primary key on everything about an aeroplane, because registrations move
 * between airframes and a foreign key onto one would follow the paint. Deleting a tail takes its
 * bulletins and its readings with it.
 */
@Entity
@Table(name = "aircraft", indexes = @Index(name = "ix_aircraft_registration", columnList = "registration"))
public class AircraftRow extends Recorded {

    @Id
    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Column(name = "registration", length = 8, nullable = false)
    private Registration registration;

    @Column(name = "series", length = 8, nullable = false)
    private String series;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "delivered", nullable = false)
    private LocalDate delivered;

    @Column(name = "withdrawn")
    private LocalDate withdrawn;

    @OneToMany(mappedBy = "aircraft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmbodimentRow> embodiments = new ArrayList<>();

    @OneToMany(mappedBy = "aircraft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReadingRow> readings = new ArrayList<>();

    /** An empty row, for the persistence provider. */
    public AircraftRow() {}

    /** A row for one tail with no bulletins on it. */
    public AircraftRow(Serial serial, Registration registration, String series, int lineNumber, LocalDate delivered) {
        this.serial = serial.number();
        this.registration = registration;
        this.series = series;
        this.lineNumber = lineNumber;
        this.delivered = delivered;
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public String getSeries() {
        return series;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public LocalDate getDelivered() {
        return delivered;
    }

    public Optional<LocalDate> getWithdrawn() {
        return Optional.ofNullable(withdrawn);
    }

    public void setWithdrawn(LocalDate withdrawn) {
        this.withdrawn = withdrawn;
    }

    /** The bulletins on this tail, in the order they were stored. */
    public List<EmbodimentRow> getEmbodiments() {
        return Collections.unmodifiableList(embodiments);
    }

    /** Hang bulletins off this tail, replacing any it had. */
    public void setEmbodiments(List<EmbodimentRow> rows) {
        embodiments.clear();
        for (EmbodimentRow row : rows) {
            row.attachTo(this);
            embodiments.add(row);
        }
    }

    /** The readings on this tail, in the order they were stored. */
    public List<ReadingRow> getReadings() {
        return Collections.unmodifiableList(readings);
    }

    /** {@code <AircraftRow MSN 5127 N412VD>}, for a log. */
    @Override
    public String toString() {
        return "<AircraftRow " + getSerial() + " " + registration + ">";
    }
}
