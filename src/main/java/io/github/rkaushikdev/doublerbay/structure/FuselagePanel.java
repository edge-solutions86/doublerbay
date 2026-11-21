package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One piece of barrel skin, bounded by two frames and two stringers. */
public record FuselagePanel(
        PanelId part,
        Frame forward,
        Frame aft,
        Stringer upper,
        Stringer lower,
        Zone zone,
        PanelKind kind,
        Material material,
        Length nominal)
        implements Panel {

    /** A barrel panel with its bounds the right way round, a thickness, and a barrel kind. */
    public FuselagePanel {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(aft, "aft");
        Objects.requireNonNull(upper, "upper");
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(nominal, "nominal");
        if (forward.isAtLeast(aft)) {
            throw new BadReading(part + ": " + forward + " is not forward of " + aft);
        }
        if (!upper.isAbove(lower)) {
            throw new BadReading(part + ": " + upper + " is not above " + lower);
        }
        if (upper.side() != lower.side() && !upper.isCrown()) {
            throw new BadReading(part + ": a panel does not straddle the crown");
        }
        if (nominal.mils() <= 0) {
            throw new BadReading(part + ": a panel has a thickness");
        }
        if (kind == PanelKind.WING_UPPER || kind == PanelKind.WING_LOWER) {
            throw new BadReading(part + ": " + kind + " is not a fuselage panel");
        }
    }

    /** Barrel skin is filed under the fuselage chapter. */
    @Override
    public Chapter chapter() {
        return Chapter.FUSELAGE;
    }

    /** The hand the panel is on, which is the lower stringer's. */
    @Override
    public Side side() {
        return lower.side();
    }

    /** How many frame pitches the panel spans. */
    public int framePitches() {
        return forward.spansTo(aft);
    }

    /** How many stringer pitches the panel spans. */
    public int stringerPitches() {
        return upper.spansTo(lower);
    }

    @Override
    public int bayCount() {
        return framePitches() * stringerPitches();
    }

    /** Every single bay the panel covers, forward to aft and crown down. */
    public List<Bay> bays() {
        List<Bay> found = new ArrayList<>();
        for (int frame = forward.number(); frame < aft.number(); frame++) {
            for (int offset = 0; offset < stringerPitches(); offset++) {
                Stringer bayUpper = stringer(upper.number() + offset);
                Stringer bayLower = stringer(upper.number() + offset + 1);
                found.add(new Bay(new Frame(frame), new Frame(frame + 1), bayUpper, bayLower));
            }
        }
        return List.copyOf(found);
    }

    private Stringer stringer(int number) {
        if (number == 0) {
            return new Stringer(0);
        }
        return new Stringer(number, side());
    }

    /** Whether a frame is on the panel, its bounding frames included. */
    public boolean spansFrame(Frame frame) {
        return forward.isAtMost(frame) && frame.isAtMost(aft);
    }

    /** Whether a stringer is on the panel, on its hand, its bounding stringers included. */
    public boolean spansStringer(Stringer stringer) {
        if (stringer.side() != side() && !stringer.isCrown()) {
            return false;
        }
        return upper.number() <= stringer.number() && stringer.number() <= lower.number();
    }

    /** Whether a bay lies wholly on the panel. */
    public boolean covers(Bay bay) {
        return spansFrame(bay.forward())
                && spansFrame(bay.aft())
                && spansStringer(bay.upper())
                && spansStringer(bay.lower());
    }

    /** {@code 5310-142L (pressurised skin)}. */
    @Override
    public String toString() {
        return part + " (" + kind + ")";
    }
}
