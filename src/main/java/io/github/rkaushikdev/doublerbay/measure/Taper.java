package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A blend-out runout slope, written as a run per one of depth: {@code 20:1}.
 *
 * <p>Twenty to one means half a thousandth of depth costs ten thousandths of run in every
 * direction. The trap that catches people is that the bigger number is the gentler blend, so a
 * taper of 30:1 satisfies a requirement for 20:1 and never the other way round.
 */
public final class Taper implements Comparable<Taper> {

    private static final Pattern SHAPE = Pattern.compile("([0-9]+)\\s*:\\s*1");

    private final int run;

    /** A taper of a run per one of depth, at least one to one. */
    public Taper(int run) {
        if (run < 1) {
            throw new BadReading("a taper runs at least one to one, got " + run);
        }
        this.run = run;
    }

    /** A taper as the shop writes it, {@code 20:1}. */
    public static Taper parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a taper, write it as 20:1");
        }
        return new Taper(Integer.parseInt(match.group(1)));
    }

    /** The run per one of depth. */
    public int run() {
        return run;
    }

    /** How far out from the deepest point the blend has to feather. */
    public Length runout(Length depth) {
        return depth.times(run);
    }

    /** Whether this blend is gentler than another, which means a bigger run. */
    public boolean isGentlerThan(Taper other) {
        return run > other.run;
    }

    /** A gentler blend meets a requirement for a sharper one. */
    public boolean satisfies(Taper required) {
        return run >= required.run;
    }

    @Override
    public int compareTo(Taper other) {
        return Integer.compare(run, other.run);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Taper taper && taper.run == run;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(run);
    }

    /** {@code 20:1}. */
    @Override
    public String toString() {
        return run + ":1";
    }
}
