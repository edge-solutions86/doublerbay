package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;

/**
 * What a tail is flying at, taken off the recent stretch of its log.
 *
 * @param serial the tail, as it was asked for
 * @param asOf the day of the last reading the rate was taken to
 * @param cyclesADay cycles flown a day, as written
 * @param hoursADay hours flown a day, as written
 * @param averageLeg the average hours a cycle over that stretch, as written
 */
public record RateOut(String serial, LocalDate asOf, String cyclesADay, String hoursADay, String averageLeg) {}
