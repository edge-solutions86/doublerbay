package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page's worth of limits, resolved against a thickness when it is read.
 *
 * <p>The numbers on a limit page, and the fact that half of them are not numbers. A limit page
 * rarely says a damage may be five thousandths deep. It says five thousandths or ten percent of the
 * skin thickness, whichever is less, and it says the remaining thickness must not fall below eighty
 * percent, and it says edge margin is at least two diameters. Every one of those depends on something
 * that is not on the page: the thickness where the damage actually is, and the size of the fastener
 * nearest to it.
 *
 * <p>That is why a limit set is a set of rules rather than a row of numbers, and why resolving it
 * takes arguments. The rule for combining an absolute cap with a fractional one is that the tighter
 * of the two wins, and for a floor it is the higher of the two. Getting that backwards clears damage
 * the manual does not.
 *
 * <p>Start from {@link #allowable()} or {@link #nothingAllowable()} and add each limit the page
 * states with its {@code withX} method. Two limit sets are equal when every limit is the same
 * amount, however many places an area or a count of diameters was written to.
 */
public record LimitSet(
        boolean allowed,
        Optional<Length> maxDepth,
        Optional<Ratio> maxDepthFraction,
        Optional<Length> maxLength,
        Optional<BigDecimal> maxArea,
        Optional<Ratio> maxSharpness,
        Optional<Length> minResidual,
        Optional<Ratio> minResidualFraction,
        Optional<Length> minEdgeMargin,
        Optional<BigDecimal> minEdgeMarginDiameters,
        Optional<Taper> blendTaper) {

    /** A limit set that carries numbers only when it allows something, and sensible ones. */
    public LimitSet {
        Objects.requireNonNull(maxDepth, "maxDepth");
        Objects.requireNonNull(maxDepthFraction, "maxDepthFraction");
        Objects.requireNonNull(maxLength, "maxLength");
        Objects.requireNonNull(maxArea, "maxArea");
        Objects.requireNonNull(maxSharpness, "maxSharpness");
        Objects.requireNonNull(minResidual, "minResidual");
        Objects.requireNonNull(minResidualFraction, "minResidualFraction");
        Objects.requireNonNull(minEdgeMargin, "minEdgeMargin");
        Objects.requireNonNull(minEdgeMarginDiameters, "minEdgeMarginDiameters");
        Objects.requireNonNull(blendTaper, "blendTaper");
        if (!allowed
                && anyPresent(
                        maxDepth,
                        maxDepthFraction,
                        maxLength,
                        maxArea,
                        maxSharpness,
                        minResidual,
                        minResidualFraction)) {
            throw new BadReading("a limit set that allows nothing does not carry limits");
        }
        if (maxArea.isPresent() && maxArea.get().signum() <= 0) {
            throw new BadReading("an area limit is an area");
        }
        if (minEdgeMarginDiameters.isPresent() && minEdgeMarginDiameters.get().signum() <= 0) {
            throw new BadReading("an edge margin in diameters is a distance");
        }
    }

    /** A page that allows the damage and, until a limit is added, caps nothing. */
    public static LimitSet allowable() {
        return new LimitSet(
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** A page that says this damage is never acceptable as it stands. */
    public static LimitSet nothingAllowable() {
        return new LimitSet(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static boolean anyPresent(Optional<?>... values) {
        for (Optional<?> value : values) {
            if (value.isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean carriesANumber() {
        return anyPresent(
                maxDepth, maxDepthFraction, maxLength, maxArea, maxSharpness, minResidual, minResidualFraction);
    }

    /** The same limits with an absolute depth cap. */
    public LimitSet withMaxDepth(Length depth) {
        return new LimitSet(
                allowed,
                Optional.of(depth),
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with a depth cap written as a share of the thickness at the damage. */
    public LimitSet withMaxDepthFraction(Ratio fraction) {
        return new LimitSet(
                allowed,
                maxDepth,
                Optional.of(fraction),
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with a length cap. */
    public LimitSet withMaxLength(Length length) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                Optional.of(length),
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with an area cap, in square inches. */
    public LimitSet withMaxArea(BigDecimal area) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                Optional.of(area),
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with a cap on how sharp a dent may be. */
    public LimitSet withMaxSharpness(Ratio sharpness) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                Optional.of(sharpness),
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with an absolute floor on the metal left. */
    public LimitSet withMinResidual(Length residual) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                Optional.of(residual),
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with a floor on the metal left, as a share of the thickness. */
    public LimitSet withMinResidualFraction(Ratio fraction) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                Optional.of(fraction),
                minEdgeMargin,
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with an edge margin in inches. */
    public LimitSet withMinEdgeMargin(Length margin) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                Optional.of(margin),
                minEdgeMarginDiameters,
                blendTaper);
    }

    /** The same limits with an edge margin in diameters of the nearest fastener. */
    public LimitSet withMinEdgeMarginDiameters(BigDecimal diameters) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                Optional.of(diameters),
                blendTaper);
    }

    /** The same limits with the taper a blend has to run out at. */
    public LimitSet withBlendTaper(Taper taper) {
        return new LimitSet(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea,
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters,
                Optional.of(taper));
    }

    /** A page that allows something but caps nothing, which the audit looks for. */
    public boolean isOpen() {
        return allowed && !carriesANumber();
    }

    /** The deepest the damage may be, taking the tighter of the two forms. */
    public Optional<Length> depthCap(Length thickness) {
        List<Length> caps = new ArrayList<>();
        maxDepth.ifPresent(caps::add);
        fractionOf(maxDepthFraction, thickness).ifPresent(caps::add);
        return caps.stream().reduce(Length::min);
    }

    /** The least metal that may be left, taking the higher of the two forms. */
    public Optional<Length> residualFloor(Length thickness) {
        List<Length> floors = new ArrayList<>();
        minResidual.ifPresent(floors::add);
        fractionOf(minResidualFraction, thickness).ifPresent(floors::add);
        return floors.stream().reduce(Length::max);
    }

    /** The least distance to an edge, for a page that writes it in inches if it writes it at all. */
    public Optional<Length> edgeMarginFloor() {
        return edgeMarginFloor(Optional.empty());
    }

    /** The least distance to an edge, in inches or in diameters of the nearest fastener. */
    public Optional<Length> edgeMarginFloor(FastenerSize size) {
        return edgeMarginFloor(Optional.of(size));
    }

    /**
     * The least distance to an edge, in inches or in diameters of the nearest fastener, which may not
     * be known.
     *
     * @throws BadReading when the page writes it in diameters and there is no fastener to read it by
     */
    public Optional<Length> edgeMarginFloor(Optional<FastenerSize> size) {
        List<Length> floors = new ArrayList<>();
        minEdgeMargin.ifPresent(floors::add);
        if (minEdgeMarginDiameters.isPresent()) {
            if (size.isEmpty()) {
                throw new BadReading("an edge margin in diameters needs a fastener size to read it");
            }
            floors.add(size.get().diameters(minEdgeMarginDiameters.get()));
        }
        return floors.stream().reduce(Length::max);
    }

    /** What the depth cap leaves behind, which the residual floor has to agree with. */
    public Optional<Length> impliedResidual(Length thickness) {
        return depthCap(thickness).map(thickness::minus);
    }

    private static Optional<Length> fractionOf(Optional<Ratio> ratio, Length thickness) {
        return ratio.map(share -> share.of(thickness));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LimitSet that
                && allowed == that.allowed
                && maxDepth.equals(that.maxDepth)
                && maxDepthFraction.equals(that.maxDepthFraction)
                && maxLength.equals(that.maxLength)
                && sameAmount(maxArea, that.maxArea)
                && maxSharpness.equals(that.maxSharpness)
                && minResidual.equals(that.minResidual)
                && minResidualFraction.equals(that.minResidualFraction)
                && minEdgeMargin.equals(that.minEdgeMargin)
                && sameAmount(minEdgeMarginDiameters, that.minEdgeMarginDiameters)
                && blendTaper.equals(that.blendTaper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowed,
                maxDepth,
                maxDepthFraction,
                maxLength,
                maxArea.map(BigDecimal::stripTrailingZeros),
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters.map(BigDecimal::stripTrailingZeros),
                blendTaper);
    }

    private static boolean sameAmount(Optional<BigDecimal> one, Optional<BigDecimal> other) {
        if (one.isEmpty() || other.isEmpty()) {
            return one.isEmpty() && other.isEmpty();
        }
        return one.get().compareTo(other.get()) == 0;
    }

    /**
     * {@code depth 0.005, sharpness 2.00%}: the depth caps, length, area, sharpness and residual
     * share, or {@code nothing allowable}, or {@code no limits stated}.
     */
    @Override
    public String toString() {
        if (!allowed) {
            return "nothing allowable";
        }
        List<String> parts = new ArrayList<>();
        maxDepth.ifPresent(depth -> parts.add("depth " + depth.inches().toPlainString()));
        maxDepthFraction.ifPresent(fraction -> parts.add("depth " + fraction));
        maxLength.ifPresent(length -> parts.add("length " + length.inches().toPlainString()));
        maxArea.ifPresent(area -> parts.add("area " + Decimals.text(area)));
        maxSharpness.ifPresent(sharpness -> parts.add("sharpness " + sharpness));
        minResidualFraction.ifPresent(fraction -> parts.add("residual " + fraction));
        return parts.isEmpty() ? "no limits stated" : String.join(", ", parts);
    }
}
