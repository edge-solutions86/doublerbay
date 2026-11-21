package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.TypeDefinition;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The dent map, which is every open damage on one aeroplane put in its place.
 *
 * <p>A card on its own says where it is in words. The map turns all of them into coordinates on the
 * same skin at the same time, which is the only way to answer the two questions that need every card
 * at once: what else is near this damage, and how many are on this panel.
 *
 * <p>The second one is a limit in its own right. The manual allows a dent within a size, and it
 * separately allows only so many of them in one panel before the panel comes off whatever size they
 * are. A desk that assesses cards one at a time will pass every one of them and miss that.
 *
 * <p>The map holds every card on one tail, placed and grouped by the panel it is on, each panel's
 * cards in the order they were given.
 */
public record DentMap(
        Serial serial, TypeDefinition definition, List<Placed> placed, Map<PanelId, List<Placed>> panels) {

    /** A map of one tail on one structure. */
    public DentMap {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(definition, "definition");
        placed = List.copyOf(placed);
        Map<PanelId, List<Placed>> copied = new LinkedHashMap<>();
        panels.forEach((part, cards) -> copied.put(part, List.copyOf(cards)));
        panels = Collections.unmodifiableMap(copied);
    }

    /** Resolve one card onto the skin of the aeroplane it was raised against. */
    public static Placed place(TypeDefinition definition, Finding finding) {
        return switch (finding.point()) {
            case FuselagePoint point ->
                new Placed(finding, definition.barrel().grid().absolute(point));
            case WingPoint point ->
                new Placed(finding, definition.wingBox().gridFor(point.side()).absolute(point));
        };
    }

    /** Which panel a card sits on, on either structure. */
    public static PanelId panelOf(TypeDefinition definition, Finding finding) {
        return switch (finding.point()) {
            case FuselagePoint point ->
                definition.barrel().panelAt(point).panel().part();
            case WingPoint point -> definition.wingBox().panelAt(point).panel().part();
        };
    }

    /** Build the map, skipping nothing and refusing cards from another tail. */
    public static DentMap of(Serial serial, TypeDefinition definition, List<Finding> findings) {
        List<Placed> placed = new ArrayList<>();
        Map<PanelId, List<Placed>> grouped = new LinkedHashMap<>();
        for (Finding finding : findings) {
            if (!finding.serial().equals(serial)) {
                throw new BadReading(finding.reference() + " is not a card on " + serial);
            }
            Placed landed = place(definition, finding);
            placed.add(landed);
            grouped.computeIfAbsent(panelOf(definition, finding), ignored -> new ArrayList<>())
                    .add(landed);
        }
        return new DentMap(serial, definition, placed, grouped);
    }

    /** How many cards are on the map. */
    public int count() {
        return placed.size();
    }

    /** The panels with cards on them, in part number order. */
    public List<PanelId> parts() {
        return panels.keySet().stream()
                .sorted(Comparator.comparing(PanelId::toString))
                .toList();
    }

    /** The cards on one panel, none for a panel with nothing on it. */
    public List<Placed> onPanel(PanelId part) {
        return panels.getOrDefault(part, List.of());
    }

    /** How many cards are on one panel, which is what a repeat damage limit reads. */
    public int countOn(PanelId part) {
        return onPanel(part).size();
    }

    /** How many cards of one kind are on one panel. */
    public int countOfKindOn(PanelId part, DamageKind kind) {
        return (int) onPanel(part).stream()
                .filter(landed -> landed.finding().kind() == kind)
                .count();
    }

    /** The groups on one panel that have to be assessed together. */
    public List<Cluster> clustersOn(PanelId part) {
        List<Placed> cards = onPanel(part);
        if (cards.isEmpty()) {
            return List.of();
        }
        return Clusters.clusters(cards);
    }

    /** Whichever group a given card ends up in once its neighbours are taken in. */
    public Cluster clusterHolding(Finding finding) {
        PanelId part = panelOf(definition, finding);
        for (Cluster group : clustersOn(part)) {
            boolean holds = group.members().stream()
                    .anyMatch(member -> member.finding().reference().equals(finding.reference()));
            if (holds) {
                return group;
            }
        }
        throw new NotOnFile(finding.reference() + " is not on this map");
    }

    /** Cards falling in one bay of the barrel, which is the unit most limits are written per. */
    public List<Placed> inBay(Bay bay) {
        List<Placed> found = new ArrayList<>();
        for (Placed landed : placed) {
            if (landed.finding().point() instanceof FuselagePoint point
                    && definition.barrel().bayAt(point).equals(bay)) {
                found.add(landed);
            }
        }
        return List.copyOf(found);
    }

    /** {@code MSN 5127: 2 open on 1 panels}. */
    @Override
    public String toString() {
        return serial + ": " + count() + " open on " + panels.size() + " panels";
    }
}
