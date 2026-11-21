package io.github.rkaushikdev.doublerbay.api;

/**
 * One limit on the due list, as it goes back.
 *
 * @param serial the tail it is on
 * @param against the card it is against
 * @param what what the limit is
 * @param due where the limit lands, as written
 * @param days the days left before it lands, below nothing once it has passed
 * @param clock which clock lands first
 * @param overrun whether it has already passed
 */
public record DueItemOut(
        String serial, String against, String what, String due, int days, String clock, boolean overrun) {}
