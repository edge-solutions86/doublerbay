package io.github.rkaushikdev.doublerbay.api;

import java.util.Optional;

/**
 * One stretch between two readings of a crack, as it goes back.
 *
 * @param was the length at the earlier reading
 * @param now the length at the later reading
 * @param grewBy how much it grew between them
 * @param cycles the flight cycles between them
 * @param measurable whether the stretch is long enough to take a rate from
 * @param rate inches per thousand cycles, when it is
 */
public record StepOut(String was, String now, String grewBy, int cycles, boolean measurable, Optional<String> rate) {}
