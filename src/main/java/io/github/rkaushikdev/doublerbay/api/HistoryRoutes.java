package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.report.History;
import io.github.rkaushikdev.doublerbay.report.Story;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The story route, which is the one anybody asks for in a meeting.
 *
 * <p>Somebody says "what happened with the crack on 5127", and the answer is not one record. It is
 * the card, the moves, the deferral, the letter that went out and the doubler that finally went on,
 * in the order they happened. This route is that answer.
 *
 * <p>It is read only and it is deliberately unfiltered. A story with the boring lines removed is a
 * story somebody will accuse the desk of curating, which is the exact accusation this record exists
 * to survive.
 */
@RestController
@Tag(name = "history")
final class HistoryRoutes {

    private final Deps deps;

    HistoryRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/history/registers")
    @Operation(summary = "The registers a story reads from, in the order they read on one day.")
    RegistersOut registers() {
        return new RegistersOut(History.ORDER);
    }

    @GetMapping("/history/{reference}")
    @Operation(summary = "Everything that happened to one card, oldest first.")
    StoryOut cardHistory(@PathVariable("reference") String reference) {
        return deps.apply(unit -> storyOut(History.storyOf(unit, Dependencies.referenceFrom(reference))));
    }

    /** One story as it goes back. */
    static StoryOut storyOut(Story built) {
        return new StoryOut(
                built.reference().toString(),
                built.serial().number(),
                built.opened(),
                built.latest(),
                built.daysOpen(),
                built.entries().stream()
                        .map(one -> new StoryEntryOut(one.on(), one.register(), one.what()))
                        .toList());
    }
}
