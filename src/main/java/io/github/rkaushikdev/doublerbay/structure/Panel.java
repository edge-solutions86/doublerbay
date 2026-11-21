package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;

/**
 * A skin panel, on the barrel or on a wing: a single piece of sheet with a part number, bounded by
 * the members it is fastened to.
 *
 * <p>A panel usually covers several bays, which matters because limits are written per bay and
 * repeat damage counts are written per panel. Two dents in one bay and two dents in two bays of the
 * same panel are different questions with different answers. A panel also carries the kind it is,
 * and that is the thing the limit tables are keyed on.
 */
public sealed interface Panel permits FuselagePanel, WingPanel {

    /** The part number. */
    PanelId part();

    /** The zone the panel is in. */
    Zone zone();

    /** What the limit tables key the panel on. */
    PanelKind kind();

    /** What the panel is cut from. */
    Material material();

    /** The gauge the panel is called out at. */
    Length nominal();

    /** The manual chapter the panel is filed under. */
    Chapter chapter();

    /** The hand the panel is on. */
    Side side();

    /** How many single bays the panel covers. */
    int bayCount();
}
