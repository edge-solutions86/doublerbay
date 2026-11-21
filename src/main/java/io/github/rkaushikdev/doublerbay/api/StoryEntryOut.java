package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * One line of a card's story as it goes back.
 *
 * @param on the day it happened
 * @param cameFrom the register it came off
 * @param what what happened, in words
 */
public record StoryEntryOut(LocalDate on, String cameFrom, String what) {}
