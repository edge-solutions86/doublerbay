package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * An engineering order as it goes back.
 *
 * @param reference the order number
 * @param against the card it asks about
 * @param question what it asks
 * @param raised the day it was written
 * @param state where it is in its life
 * @param sent the day it went, once it has
 * @param answered the day the answer came back, once it has
 * @param open whether it is still waiting on somebody
 * @param approval the sheet that came back, for an approved order
 */
public record OrderOut(
        String reference,
        String against,
        String question,
        LocalDate raised,
        String state,
        Optional<LocalDate> sent,
        Optional<LocalDate> answered,
        boolean open,
        Optional<ApprovalOut> approval) {}
