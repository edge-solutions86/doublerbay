package io.github.rkaushikdev.doublerbay.tooling;

import java.util.Objects;

/** One gate that ran, and what its command exited with: nought for a pass. */
public record GateResult(Gate gate, int exitCode) {

    /** What a gate exited with. */
    public GateResult {
        Objects.requireNonNull(gate, "gate");
    }

    /** Whether the gate's command exited with anything but nought. */
    public boolean failed() {
        return exitCode != 0;
    }
}
