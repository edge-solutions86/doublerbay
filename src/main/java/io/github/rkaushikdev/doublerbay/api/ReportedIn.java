package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The day a finding went out to the authority.
 *
 * @param on the day it was reported
 */
public record ReportedIn(@NotNull LocalDate on) {}
