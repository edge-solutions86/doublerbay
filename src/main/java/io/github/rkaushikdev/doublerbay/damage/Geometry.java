package io.github.rkaushikdev.doublerbay.damage;

/**
 * What every set of damage measurements has in common, which is not much but is enough.
 *
 * <p>Whatever kind it is, two things get asked of a measurement set. What skin does it cover, so its
 * neighbours can be found, and what depth do the limit tables read off it, if any. Everything else
 * about a dent and a crack is different and should stay different.
 *
 * <p>Each measurement set answers those two itself. A finding never has to know which kind it is
 * holding to ask them, the knowledge about a dent lives on the dent, and adding a kind of measurement
 * means writing one new type here rather than editing the finding.
 *
 * <p>The depth is deliberately allowed to be nothing. A crack has a length and no depth worth
 * reading, and a caller that wants a depth off one is asking the wrong question rather than getting a
 * zero it will divide by.
 *
 * <p>The set is closed: these eight are every kind of measurement the desk takes.
 */
public sealed interface Geometry extends Measured
        permits Dent, Scoring, Crack, Corrosion, LightningBurn, Opening, Buckle, Disbond {

    /** Whether the measurements carry a depth the limit tables can read. */
    static boolean hasADepth(Measured geometry) {
        return geometry.limitDepth().isPresent();
    }
}
