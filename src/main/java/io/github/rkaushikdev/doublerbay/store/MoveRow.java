package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
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
import java.time.LocalDate;

/** One move in a card's life, with who made it. Two moves never share a place in the sequence. */
@Entity
@Table(
        name = "disposition_move",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_disposition_move_reference",
                        columnNames = {"reference", "sequence"}))
public class MoveRow extends Recorded {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "reference", length = 16, nullable = false)
    private String reference;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "was", length = 24, nullable = false)
    private String was;

    @Column(name = "now", length = 24, nullable = false)
    private String now;

    @Column(name = "\"on\"", nullable = false)
    private LocalDate on;

    @Column(name = "by", length = 64, nullable = false)
    private String by;

    @Column(name = "note", length = 256, nullable = false)
    private String note = "";

    @Column(name = "reopened", nullable = false)
    private boolean reopened;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reference",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_disposition_move_reference_disposition"))
    private DispositionRow disposition;

    /** An empty row, for the persistence provider. */
    public MoveRow() {}

    /** One move, made on a day by someone, with no note. */
    public MoveRow(FindingId reference, int sequence, String was, String now, LocalDate on, String by) {
        this.reference = reference.toString();
        this.sequence = sequence;
        this.was = was;
        this.now = now;
        this.on = on;
        this.by = by;
    }

    /** The same move, carrying a note. */
    public MoveRow withNote(String text) {
        this.note = text;
        return this;
    }

    void attachTo(DispositionRow parent) {
        this.disposition = parent;
        this.reference = parent.getReference().toString();
    }

    public FindingId getReference() {
        return FindingId.parse(reference);
    }

    public int getSequence() {
        return sequence;
    }

    public String getWas() {
        return was;
    }

    public String getNow() {
        return now;
    }

    public LocalDate getOn() {
        return on;
    }

    public String getBy() {
        return by;
    }

    public String getNote() {
        return note;
    }

    public boolean isReopened() {
        return reopened;
    }

    /** {@code <MoveRow DR-2025-004182 raised to assessed>}, for a log. */
    @Override
    public String toString() {
        return "<MoveRow " + reference + " " + was + " to " + now + ">";
    }
}
