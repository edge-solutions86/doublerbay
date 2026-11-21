package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One posted counter reading. Two on one day is a mistake and the key says so. */
@Entity
@Table(name = "reading")
@IdClass(ReadingKey.class)
public class ReadingRow extends Recorded {

    @Id
    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Id
    @Column(name = "taken", nullable = false)
    private LocalDate taken;

    @Column(name = "cycles", nullable = false)
    private Cycles cycles;

    @Column(name = "hours", nullable = false)
    private Hours hours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_reading_serial_aircraft"))
    private AircraftRow aircraft;

    /** An empty row, for the persistence provider. */
    public ReadingRow() {}

    /** The counters on one tail as read on one day. */
    public ReadingRow(Serial serial, LocalDate taken, Cycles cycles, Hours hours) {
        this.serial = serial.number();
        this.taken = taken;
        this.cycles = cycles;
        this.hours = hours;
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public LocalDate getTaken() {
        return taken;
    }

    public Cycles getCycles() {
        return cycles;
    }

    public Hours getHours() {
        return hours;
    }

    /** {@code <ReadingRow MSN 5127 2025-01-01>}, for a log. */
    @Override
    public String toString() {
        return "<ReadingRow " + getSerial() + " " + taken + ">";
    }
}
