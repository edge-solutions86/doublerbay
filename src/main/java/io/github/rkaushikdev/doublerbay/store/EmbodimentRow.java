package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;

/** A service bulletin that has gone on one tail. It goes on once. */
@Entity
@Table(
        name = "embodiment",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_embodiment_serial",
                        columnNames = {"serial", "chapter", "number"}))
public class EmbodimentRow extends Recorded {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Column(name = "chapter", nullable = false)
    private int chapter;

    @Column(name = "number", nullable = false)
    private int number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_embodiment_serial_aircraft"))
    private AircraftRow aircraft;

    /** An empty row, for the persistence provider. */
    public EmbodimentRow() {}

    /** Bulletin chapter-number on one tail. */
    public EmbodimentRow(Serial serial, int chapter, int number) {
        this.serial = serial.number();
        this.chapter = chapter;
        this.number = number;
    }

    void attachTo(AircraftRow parent) {
        this.aircraft = parent;
        this.serial = parent.getSerial().number();
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public int getChapter() {
        return chapter;
    }

    public int getNumber() {
        return number;
    }

    /** {@code <EmbodimentRow MSN 5127 SB 53-018>}, for a log. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<EmbodimentRow %s SB %02d-%03d>", getSerial(), chapter, number);
    }
}
