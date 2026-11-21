package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Station;
import java.util.Objects;

/** One frame and the station it sits at: {@code FR41 at STA 620.000}. */
public record FrameStation(Frame frame, Station station) {

    /** A frame placed at a station. */
    public FrameStation {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(station, "station");
    }

    /** {@code FR41 at STA 620.000}. */
    @Override
    public String toString() {
        return frame + " at " + station;
    }
}
