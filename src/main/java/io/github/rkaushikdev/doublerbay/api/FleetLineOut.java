package io.github.rkaushikdev.doublerbay.api;

/**
 * One tail's line on the fleet audit: the counts that decide the argument.
 *
 * @param serial the tail
 * @param totalRepairs every repair fitted to it
 * @param crowdedPanels how many of its panels are crowded
 * @param overdueTemporary how many of its temporary repairs have outlived their life
 * @param clashes how many pairs of its repairs are too close together
 * @param clean whether nothing on it is worth flagging
 */
public record FleetLineOut(
        String serial, int totalRepairs, int crowdedPanels, int overdueTemporary, int clashes, boolean clean) {}
