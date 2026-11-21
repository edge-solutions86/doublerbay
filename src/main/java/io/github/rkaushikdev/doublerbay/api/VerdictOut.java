package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import java.util.Optional;

/**
 * What the book made of a damage, with the page and revision it came off and the whole trail.
 *
 * @param disposition what happens to the damage
 * @param page the page of the manual the answer came off, if one did
 * @param revision the revision of that page, if one did
 * @param why the answer in one sentence
 * @param reasons every rule asked, in the order it was asked
 */
public record VerdictOut(
        String disposition, Optional<String> page, Optional<Integer> revision, String why, List<ReasonOut> reasons) {

    public VerdictOut {
        reasons = List.copyOf(reasons);
    }
}
