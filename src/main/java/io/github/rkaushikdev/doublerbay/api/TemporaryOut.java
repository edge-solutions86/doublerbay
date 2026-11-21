package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * A temporary repair and how long it has been fitted, as it goes back.
 *
 * @param reference the repair
 * @param part the panel it is on
 * @param embodied the day it went on
 * @param monthsFitted how long it has been on, in months, as written
 * @param overdue whether it has outlived what a temporary repair is meant to last
 */
public record TemporaryOut(String reference, String part, LocalDate embodied, String monthsFitted, boolean overdue) {}
