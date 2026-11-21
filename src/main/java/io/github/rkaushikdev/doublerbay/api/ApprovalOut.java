package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A repair approval as it goes back.
 *
 * @param sheet the approval sheet number
 * @param category the category it puts the repair in
 * @param conditions what the approval holds the operator to
 * @param validUntil the last day it holds, when it runs out
 */
public record ApprovalOut(String sheet, String category, List<String> conditions, Optional<LocalDate> validUntil) {}
