package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the desk decides, and every rule it walked to get there: one disposition, with the trail that
 * produced it.
 *
 * <p>The reason trail is not decoration. Somebody signs the disposition, and in two years an auditor
 * asks why a 0.055 dent was left on a pressurised panel. The answer has to be the page, the
 * revision, the thickness that was read, and the rules that passed, all of it recorded at the time.
 * Recomputing it later reads today's book and answers a different question.
 */
public record Verdict(
        Disposition disposition, List<Reason> reasons, Optional<Reference> reference, Optional<Integer> revision) {

    /** What a citation prints for a revision it was not given. */
    private static final String NO_REVISION = "None";

    /** A verdict that carries at least one rule. */
    public Verdict {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(revision, "revision");
        reasons = List.copyOf(reasons);
        if (reasons.isEmpty()) {
            throw new BadReading("a verdict carries the rules it walked");
        }
    }

    /** A verdict that cites no page. */
    public Verdict(Disposition disposition, List<Reason> reasons) {
        this(disposition, reasons, Optional.empty(), Optional.empty());
    }

    /** A verdict citing the page and the revision it was read against. */
    public Verdict(Disposition disposition, List<Reason> reasons, Reference reference, int revision) {
        this(disposition, reasons, Optional.of(reference), Optional.of(revision));
    }

    /** A verdict from what was to hand; a null page or revision is one it does not cite. */
    public static Verdict of(Disposition disposition, List<Reason> reasons, Reference reference, Integer revision) {
        return new Verdict(disposition, reasons, Optional.ofNullable(reference), Optional.ofNullable(revision));
    }

    /**
     * The worst answer any rule gave, carrying every trail that led to it.
     *
     * <p>The page and revision come off the first of the worst, and the reasons are every verdict's,
     * in the order they came.
     */
    public static Verdict worstOf(Iterable<Verdict> verdicts) {
        List<Verdict> collected = new ArrayList<>();
        verdicts.forEach(collected::add);
        if (collected.isEmpty()) {
            throw new BadReading("there is no worst of nothing");
        }
        Verdict worst = collected.get(0);
        for (Verdict verdict : collected) {
            if (verdict.disposition.rank() > worst.disposition.rank()) {
                worst = verdict;
            }
        }
        List<Reason> trail =
                collected.stream().flatMap(verdict -> verdict.reasons.stream()).toList();
        return new Verdict(worst.disposition, trail, worst.reference, worst.revision);
    }

    /** Whether the damage can stay as it is. */
    public boolean isAllowable() {
        return disposition == Disposition.ALLOWABLE;
    }

    /** Whether the answer puts the aeroplane in a hangar. */
    public boolean needsWork() {
        return disposition.needsWork();
    }

    /** The rules the damage did not get past, in the order they ran. */
    public List<Reason> failures() {
        return reasons.stream().filter(reason -> !reason.passed()).toList();
    }

    /** The rules the damage got past, in the order they ran. */
    public List<Reason> passes() {
        return reasons.stream().filter(Reason::passed).toList();
    }

    /** One line naming what stopped it, or what let it through. */
    public String why() {
        List<Reason> failed = failures();
        if (!failed.isEmpty()) {
            return failed.stream().map(Reason::detail).collect(Collectors.joining("; "));
        }
        return "every limit met";
    }

    /** The same verdict with one more rule on its trail. */
    public Verdict withReason(Reason reason) {
        List<Reason> longer = new ArrayList<>(reasons);
        longer.add(Objects.requireNonNull(reason, "reason"));
        return new Verdict(disposition, longer, reference, revision);
    }

    /** The same trail with a worse answer on it, which is how a cluster escalates. */
    public Verdict raisedTo(Disposition worse) {
        if (worse.rank() < disposition.rank()) {
            throw new BadReading("a verdict is not talked down once a rule has failed");
        }
        return new Verdict(worse, reasons, reference, revision);
    }

    /**
     * {@code allowable as it stands per 53-10-01 rev 12: every limit met}, or without the citation
     * when there is no page. A page cited without a revision prints its revision as {@code None}.
     */
    @Override
    public String toString() {
        String page = reference
                .map(cited -> " per " + cited + " rev "
                        + revision.map(String::valueOf).orElse(NO_REVISION))
                .orElse("");
        return disposition + page + ": " + why();
    }
}
