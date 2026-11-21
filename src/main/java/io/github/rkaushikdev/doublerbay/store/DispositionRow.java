package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Where a card is now. Its history is the moves table, which goes with it. */
@Entity
@Table(name = "disposition", indexes = @Index(name = "ix_disposition_state", columnList = "state"))
public class DispositionRow extends Recorded {

    @Id
    @Column(name = "reference", length = 16, nullable = false)
    private String reference;

    @Column(name = "state", length = 24, nullable = false)
    private String state;

    @OneToMany(mappedBy = "disposition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoveRow> moves = new ArrayList<>();

    /** An empty row, for the persistence provider. */
    public DispositionRow() {}

    /** A card in a state, with no moves yet. */
    public DispositionRow(FindingId reference, String state) {
        this.reference = reference.toString();
        this.state = state;
    }

    public FindingId getReference() {
        return FindingId.parse(reference);
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /** The moves, in the order they were stored. */
    public List<MoveRow> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    /** Hang moves off this card, replacing any it had. */
    public void setMoves(List<MoveRow> rows) {
        moves.clear();
        for (MoveRow row : rows) {
            row.attachTo(this);
            moves.add(row);
        }
    }

    /** {@code <DispositionRow DR-2025-004182 assessed>}, for a log. */
    @Override
    public String toString() {
        return "<DispositionRow " + reference + " " + state + ">";
    }
}
