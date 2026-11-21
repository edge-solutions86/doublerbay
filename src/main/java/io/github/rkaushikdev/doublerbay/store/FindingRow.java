package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * One damage card, at one revision, with its location as it was written.
 *
 * <p>The location is stored as its written form, member and offset, in one set of columns per
 * structure with a discriminator, rather than as a resolved coordinate. A resolved coordinate is
 * correct only against the grid it was resolved with, and grids get corrected. One of the two sets
 * is filled in and {@code structure} says which.
 */
@Entity
@Table(
        name = "finding",
        indexes = {
            @Index(name = "ix_finding_found_on", columnList = "found_on"),
            @Index(name = "ix_finding_kind", columnList = "kind"),
            @Index(name = "ix_finding_serial", columnList = "serial")
        })
@IdClass(FindingKey.class)
public class FindingRow extends Recorded {

    @Id
    @Column(name = "reference", length = 16, nullable = false)
    private String reference;

    @Id
    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Column(name = "kind", length = 24, nullable = false)
    private String kind;

    @Column(name = "found_on", nullable = false)
    private LocalDate foundOn;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "origin", length = 24, nullable = false)
    private String origin;

    @Column(name = "at_cycles", nullable = false)
    private Cycles atCycles;

    @Column(name = "at_hours", nullable = false)
    private Hours atHours;

    @Column(name = "structure", length = 8, nullable = false)
    private String structure;

    @Column(name = "frame")
    private Integer frame;

    @Column(name = "aft_of_frame")
    private Length aftOfFrame;

    @Column(name = "stringer")
    private Integer stringer;

    @Column(name = "below_stringer")
    private Length belowStringer;

    @Column(name = "rib")
    private Integer rib;

    @Column(name = "outboard_of_rib")
    private Length outboardOfRib;

    @Column(name = "spar", length = 8)
    private String spar;

    @Column(name = "aft_of_spar")
    private Length aftOfSpar;

    @Column(name = "surface", length = 8)
    private String surface;

    @Column(name = "side", length = 1, nullable = false)
    private String side;

    @Convert(converter = MeasurementsColumn.class)
    @Column(name = "measurements", length = 4096, nullable = false)
    private Map<String, Object> measurements;

    @Column(name = "superseded_by", length = 16)
    private FindingId supersededBy;

    @Column(name = "note", length = 256, nullable = false)
    private String note = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_finding_serial_aircraft"))
    private AircraftRow aircraft;

    /** An empty row, for the persistence provider. */
    public FindingRow() {}

    /** The columns every card carries, whichever structure it is on. */
    public FindingRow(
            FindingId reference,
            int revision,
            Serial serial,
            String kind,
            LocalDate foundOn,
            String source,
            String origin,
            Cycles atCycles,
            Hours atHours,
            String structure,
            String side,
            Map<String, Object> measurements) {
        this.reference = reference.toString();
        this.revision = revision;
        this.serial = serial.number();
        this.kind = kind;
        this.foundOn = foundOn;
        this.source = source;
        this.origin = origin;
        this.atCycles = atCycles;
        this.atHours = atHours;
        this.structure = structure;
        this.side = side;
        this.measurements = measurements;
    }

    /** Where on the fuselage, as written: frame, inches aft of it, stringer, inches below it. */
    public FindingRow onFuselage(int frameNumber, Length aft, int stringerNumber, Length below) {
        this.frame = frameNumber;
        this.aftOfFrame = aft;
        this.stringer = stringerNumber;
        this.belowStringer = below;
        return this;
    }

    /** Where on the wing, as written: rib, inches outboard, spar, inches aft, surface. */
    public FindingRow onWing(int ribNumber, Length outboard, String sparName, Length aft, String surfaceName) {
        this.rib = ribNumber;
        this.outboardOfRib = outboard;
        this.spar = sparName;
        this.aftOfSpar = aft;
        this.surface = surfaceName;
        return this;
    }

    public FindingId getReference() {
        return FindingId.parse(reference);
    }

    public int getRevision() {
        return revision;
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public String getKind() {
        return kind;
    }

    public LocalDate getFoundOn() {
        return foundOn;
    }

    public String getSource() {
        return source;
    }

    public String getOrigin() {
        return origin;
    }

    public Cycles getAtCycles() {
        return atCycles;
    }

    public Hours getAtHours() {
        return atHours;
    }

    public String getStructure() {
        return structure;
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public Optional<Integer> getFrame() {
        return Optional.ofNullable(frame);
    }

    public Optional<Length> getAftOfFrame() {
        return Optional.ofNullable(aftOfFrame);
    }

    public Optional<Integer> getStringer() {
        return Optional.ofNullable(stringer);
    }

    public Optional<Length> getBelowStringer() {
        return Optional.ofNullable(belowStringer);
    }

    public Optional<Integer> getRib() {
        return Optional.ofNullable(rib);
    }

    public Optional<Length> getOutboardOfRib() {
        return Optional.ofNullable(outboardOfRib);
    }

    public Optional<String> getSpar() {
        return Optional.ofNullable(spar);
    }

    public Optional<Length> getAftOfSpar() {
        return Optional.ofNullable(aftOfSpar);
    }

    public Optional<String> getSurface() {
        return Optional.ofNullable(surface);
    }

    public String getSide() {
        return side;
    }

    public Map<String, Object> getMeasurements() {
        return measurements;
    }

    public void setMeasurements(Map<String, Object> measurements) {
        this.measurements = measurements;
    }

    public Optional<FindingId> getSupersededBy() {
        return Optional.ofNullable(supersededBy);
    }

    public void setSupersededBy(FindingId supersededBy) {
        this.supersededBy = supersededBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** {@code <FindingRow DR-2025-004182 rev 1>}, for a log. */
    @Override
    public String toString() {
        return "<FindingRow " + reference + " rev " + revision + ">";
    }
}
