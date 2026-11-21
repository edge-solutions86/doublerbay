package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.math.BigDecimal;
import java.util.Objects;

/** One line of fasteners, offset from the member the joint follows: {@code -5 at 0.750 csk}. */
public record FastenerRow(FastenerSize size, Length pitch, Length offset, boolean countersunk) {

    /** A row with a pitch no closer than its fastener size allows. */
    public FastenerRow {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(offset, "offset");
        if (pitch.mils() <= 0) {
            throw new BadReading("a row has a pitch");
        }
        if (pitch.isLessThan(size.minPitch())) {
            throw new BadReading(pitch + " is closer than the " + size.minPitch() + " minimum for " + size);
        }
    }

    /** A row of protruding head fasteners. */
    public FastenerRow(FastenerSize size, Length pitch, Length offset) {
        this(size, pitch, offset, false);
    }

    /** The pitch read back in diameters, to two places. */
    public BigDecimal pitchInDiameters() {
        return size.inDiameters(pitch);
    }

    /** How many fasteners a run of this length carries, both ends included. */
    public int fastenerCount(Length run) {
        if (run.mils() < 0) {
            throw new BadReading("a run does not go backwards");
        }
        return run.mils() / pitch.mils() + 1;
    }

    /** How long a run of that many fasteners reaches. */
    public Length runFor(int count) {
        if (count < 1) {
            throw new BadReading("a row holds at least one fastener");
        }
        return pitch.times(count - 1);
    }

    /** {@code -5 at 0.750}, with {@code csk} after it for a countersunk row. */
    @Override
    public String toString() {
        String head = size + " at " + pitch.inches().toPlainString();
        return countersunk ? head + " csk" : head;
    }
}
