package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One graded corrosion finding, which the programme reports on separately from the damage register.
 *
 * <p>Deciding a level two means asking whether this panel on this aeroplane has corroded before this
 * day, which is exactly the columns of {@code ix_corrosion_here_before} in that order.
 */
@Entity
@Table(
        name = "corrosion_entry",
        indexes = {
            @Index(name = "ix_corrosion_entry_against", columnList = "against"),
            @Index(name = "ix_corrosion_entry_found_on", columnList = "found_on"),
            @Index(name = "ix_corrosion_entry_level", columnList = "level"),
            @Index(name = "ix_corrosion_entry_part", columnList = "part"),
            @Index(name = "ix_corrosion_entry_serial", columnList = "serial"),
            @Index(name = "ix_corrosion_here_before", columnList = "serial, part, found_on")
        })
public class CorrosionRow extends Recorded {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Column(name = "against", length = 16, nullable = false)
    private FindingId against;

    @Column(name = "part", length = 16, nullable = false)
    private PanelId part;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "found_on", nullable = false)
    private LocalDate foundOn;

    @Column(name = "reported_on")
    private LocalDate reportedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_corrosion_entry_serial_aircraft"))
    private AircraftRow aircraft;

    /** An empty row, filled in through its setters. */
    public CorrosionRow() {}

    public Integer getId() {
        return id;
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public CorrosionRow setSerial(Serial serial) {
        this.serial = serial.number();
        return this;
    }

    public FindingId getAgainst() {
        return against;
    }

    public CorrosionRow setAgainst(FindingId against) {
        this.against = against;
        return this;
    }

    public PanelId getPart() {
        return part;
    }

    public CorrosionRow setPart(PanelId part) {
        this.part = part;
        return this;
    }

    public int getLevel() {
        return level;
    }

    public CorrosionRow setLevel(int level) {
        this.level = level;
        return this;
    }

    public LocalDate getFoundOn() {
        return foundOn;
    }

    public CorrosionRow setFoundOn(LocalDate foundOn) {
        this.foundOn = foundOn;
        return this;
    }

    public Optional<LocalDate> getReportedOn() {
        return Optional.ofNullable(reportedOn);
    }

    public CorrosionRow setReportedOn(LocalDate reportedOn) {
        this.reportedOn = reportedOn;
        return this;
    }

    /** {@code <CorrosionRow 5310-143L level 3>}, for a log. */
    @Override
    public String toString() {
        return "<CorrosionRow " + part + " level " + level + ">";
    }
}
