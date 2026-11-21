package io.github.rkaushikdev.doublerbay.api;

/**
 * The drawing of a doubler, in the units the shop measures in.
 *
 * @param material the sheet it is cut from
 * @param gauge how thick it is, in inches
 * @param along how long it is, in inches
 * @param around how wide it is, in inches
 * @param fastenerDash the fastener size, as its dash number
 * @param fastenerCount every hole in the patch
 * @param rowsEachSide how many rows run along each side
 * @param perRow how many fasteners each row holds
 * @param pitch the spacing along a row, in inches
 * @param rowSpacing the spacing between rows, in inches
 * @param edgeDistance from the outer row to the doubler's edge, in inches
 */
public record DoublerOut(
        String material,
        String gauge,
        String along,
        String around,
        int fastenerDash,
        int fastenerCount,
        int rowsEachSide,
        int perRow,
        String pitch,
        String rowSpacing,
        String edgeDistance) {}
