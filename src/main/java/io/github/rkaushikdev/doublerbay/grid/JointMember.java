package io.github.rkaushikdev.doublerbay.grid;

/**
 * A barrel member a joint can follow and a margin can be measured off: a frame or a stringer.
 *
 * <p>A lap joint runs along a stringer and a butt joint runs around a frame, and the nearest member
 * bounding a bay is one or the other, so the two share this one name wherever either will do.
 */
public sealed interface JointMember permits Frame, Stringer {}
