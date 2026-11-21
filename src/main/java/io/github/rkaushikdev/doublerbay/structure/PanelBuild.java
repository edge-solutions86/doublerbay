package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A panel with everything that has been done to it, and what is actually at one spot on it once
 * everything on it is taken together.
 *
 * <p>A panel is not a rectangle of aluminium. It is a rectangle of aluminium with a chem mill pattern
 * in it, a lap joint down one edge, three rows of fasteners in that joint, a window in the middle, and
 * somebody's doubler from four years ago over in the corner. Damage lands on one spot and every one
 * of those features has an opinion about it.
 *
 * <p>They do not all get an equal say. The tightest rule wins, in the order {@link Region} fixes. The
 * thickness comes out of the same call, because it is read at the same spot and reading it separately
 * is how the two end up describing different places.
 */
public record PanelBuild(
        FuselagePanel panel,
        ThicknessMap thickness,
        List<Joint> joints,
        List<Cutout> cutouts,
        List<InstalledDoubler> doublers) {

    /** A build whose land is at least the panel gauge, with no two cutouts of the same name. */
    public PanelBuild {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(thickness, "thickness");
        joints = List.copyOf(joints);
        cutouts = List.copyOf(cutouts);
        doublers = List.copyOf(doublers);
        if (thickness.land().isLessThan(panel.nominal())) {
            throw new BadReading(panel.part() + ": the land is thinner than the panel nominal gauge");
        }
        List<String> names = cutouts.stream().map(Cutout::name).toList();
        if (new HashSet<>(names).size() != names.size()) {
            throw new BadReading(panel.part() + ": two cutouts with the same name");
        }
    }

    /** A panel with nothing on it but its thickness. */
    public PanelBuild(FuselagePanel panel, ThicknessMap thickness) {
        this(panel, thickness, List.of(), List.of(), List.of());
    }

    /** The same build with these joints. */
    public PanelBuild withJoints(List<Joint> replaced) {
        return new PanelBuild(panel, thickness, replaced, cutouts, doublers);
    }

    /** The same build with these cutouts. */
    public PanelBuild withCutouts(List<Cutout> replaced) {
        return new PanelBuild(panel, thickness, joints, replaced, doublers);
    }

    /** The same build with these installed doublers. */
    public PanelBuild withDoublers(List<InstalledDoubler> replaced) {
        return new PanelBuild(panel, thickness, joints, cutouts, replaced);
    }

    /** The same build with this thickness map. */
    public PanelBuild withThickness(ThicknessMap replaced) {
        return new PanelBuild(panel, replaced, joints, cutouts, doublers);
    }

    /** The joint that follows a member, if the panel has one on it. */
    public Optional<Joint> jointOn(JointMember member) {
        for (Joint joint : joints) {
            if (joint.follows().equals(member)) {
                return Optional.of(joint);
            }
        }
        return Optional.empty();
    }

    /** The cutout whose keep out band a spot is in, nearest edge first. */
    public Optional<Cutout> cutoutOver(Offset local) {
        Cutout nearest = null;
        int best = 0;
        for (Cutout cutout : cutouts) {
            if (!cutout.inKeepOut(local)) {
                continue;
            }
            int distance = cutout.edgeDistance(local).mils();
            if (nearest == null || distance < best) {
                nearest = cutout;
                best = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** The installed doubler a spot is under, if there is one. */
    public Optional<InstalledDoubler> doublerOver(Offset local) {
        for (InstalledDoubler doubler : doublers) {
            if (doubler.footprint().contains(local)) {
                return Optional.of(doubler);
            }
        }
        return Optional.empty();
    }

    /** The skin thickness this far in from the nearest member, with no bay to look for a pad on. */
    public Length thicknessAt(Length margin) {
        return thickness.at(margin);
    }

    /**
     * The skin thickness this far in from the nearest member.
     *
     * @param bay the bay the point is in, or null when it is not known, which reads no pad
     */
    public Length thicknessAt(Length margin, Bay bay) {
        return thickness.at(margin, bay);
    }

    /** Classify a spot with no bay to look for a pad on. */
    public SkinSpot at(Offset local, JointMember member, Length margin) {
        return at(local, member, margin, null);
    }

    /**
     * Classify a spot, taking the tightest feature that reaches it.
     *
     * @param local the spot in skin coordinates
     * @param member the nearest member bounding the spot
     * @param margin how far the spot is in from that member
     * @param bay the bay the spot is in, or null when it is not known, which reads no pad
     */
    public SkinSpot at(Offset local, JointMember member, Length margin, Bay bay) {
        Length gauge = thicknessAt(margin, bay);

        Optional<InstalledDoubler> doubler = doublerOver(local);
        if (doubler.isPresent()) {
            return new SkinSpot(
                    Region.UNDER_DOUBLER,
                    gauge.plus(doubler.get().gauge()),
                    doubler.get().part());
        }

        Optional<Cutout> cutout = cutoutOver(local);
        if (cutout.isPresent()) {
            Region region = cutout.get().nearACorner(local) ? Region.CUTOUT_CORNER : Region.CUTOUT_EDGE;
            return new SkinSpot(region, gauge, cutout.get().name());
        }

        Optional<Joint> joint = jointOn(member);
        if (joint.isPresent() && !joint.get().rows().isEmpty()) {
            FastenerRow row = joint.get().rowNearest(margin);
            if (joint.get().distanceToNearestRow(margin).isAtMost(row.size().minEdgeDistance())) {
                return new SkinSpot(Region.FASTENER_ROW, gauge, joint.get().kind() + " " + row);
            }
            if (joint.get().isWithin(margin)) {
                return new SkinSpot(Region.JOINT_BAND, gauge, joint.get().kind().toString());
            }
        }

        if (thickness.onTheStep(margin)) {
            return new SkinSpot(Region.CHEM_MILL_STEP, gauge, "chem mill step");
        }
        if (margin.isAtMost(thickness.landWidth()) && !thickness.isFlat()) {
            return new SkinSpot(Region.LAND, gauge, "land at " + member);
        }
        return new SkinSpot(Region.OPEN_FIELD, gauge, "bay off " + member);
    }
}
