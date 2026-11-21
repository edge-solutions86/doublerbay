package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shape of a refusal, which is a promise to every client that reads one.
 *
 * <p>A client branches on something in this body. Whatever it branches on cannot change once it has
 * shipped, and the thing most likely to change is the class name, because somebody will rename a
 * class for tidiness one afternoon. So there is a slug, it is fixed, and this file is where breaking
 * it fails.
 */
class ProblemShapeTest {

    private static Client api() {
        return Api.clientFor(Desk.newDatabase());
    }

    private static final class Unheard extends DoublerbayError {
        Unheard(String message) {
            super(message);
        }
    }

    @Test
    void everyRefusalThisServiceRaisesHasASlug() {
        for (DoublerbayError error :
                List.of(new BadReading("x"), new Refused("x"), new NotOnFile("x"), new OutOfSequence("x"))) {
            assertThat(Problem.slugFor(error)).isNotEmpty();
        }
    }

    @Test
    void theSlugsAreAllDifferent() {
        assertThat(new HashSet<>(Problem.SLUG_FOR.values())).hasSize(Problem.SLUG_FOR.size());
    }

    @Test
    void theSlugsAreLowerCaseAndHyphenated() {
        for (String slug : Problem.SLUG_FOR.values()) {
            assertThat(slug).isEqualTo(slug.toLowerCase(Locale.ROOT));
            assertThat(slug).doesNotContain(" ");
        }
    }

    @Test
    void aRefusalNobodyMappedStillGetsASlug() {
        assertThat(Problem.slugFor(new Unheard("x"))).isEqualTo(Problem.UNKNOWN_SLUG);
    }

    @Test
    void aLookupMissCouldWorkLater() {
        assertThat(Problem.isRetryable(new NotOnFile("x"))).isTrue();
    }

    @Test
    void aMoveOutOfSequenceCouldWorkLater() {
        assertThat(Problem.isRetryable(new OutOfSequence("x"))).isTrue();
    }

    @Test
    void aBadReadingWillNeverWork() {
        assertThat(Problem.isRetryable(new BadReading("x"))).isFalse();
    }

    @Test
    void aRefusalOfPermissionWillNeverWork() {
        assertThat(Problem.isRetryable(new Refused("x", "y"))).isFalse();
    }

    @Test
    void aProblemCarriesItsSlugAndItsClass() {
        Problem problem = Problem.of(new NotOnFile("nothing here"));
        assertThat(problem.type()).isEqualTo("not-on-file");
        assertThat(problem.kind()).isEqualTo("NotOnFile");
    }

    @Test
    void aProblemCarriesWhereItHappened() {
        assertThat(Problem.of(new NotOnFile("x"), "/aircraft/9999").where()).isEqualTo("/aircraft/9999");
    }

    @Test
    void aProblemWithNoPathSaysNothingAboutWhere() {
        assertThat(Problem.of(new NotOnFile("x")).where()).isEmpty();
    }

    @Test
    void theStatusFollowsTheKind() {
        assertThat(Problem.statusFor(new NotOnFile("x"))).isEqualTo(404);
        assertThat(Problem.statusFor(new BadReading("x"))).isEqualTo(422);
        assertThat(Problem.statusFor(new OutOfSequence("x"))).isEqualTo(409);
    }

    @Test
    void aRefusalNobodyMappedIsAServerProblem() {
        assertThat(Problem.statusFor(new Unheard("x"))).isEqualTo(500);
    }

    @Test
    void aLookupMissOverTheWireCarriesTheSlug() {
        JsonNode body = api().get("/aircraft/9999").json();
        assertThat(body.get("type").asText()).isEqualTo("not-on-file");
        assertThat(body.get("retryable").asBoolean()).isTrue();
    }

    @Test
    void aLookupMissOverTheWireNamesThePath() {
        assertThat(api().get("/aircraft/9999").json().get("where").asText()).isEqualTo("/aircraft/9999");
    }

    @Test
    void aBadReadingOverTheWireIsNotRetryable() {
        JsonNode body = api().post(
                        "/aircraft",
                        json(
                                "serial", "5240",
                                "registration", "not-a-tail",
                                "series", "200",
                                "line_number", 1140,
                                "delivered", "2016-04-12"))
                .json();
        assertThat(body.get("type").asText()).isEqualTo("bad-reading");
        assertThat(body.get("retryable").asBoolean()).isFalse();
    }

    @Test
    void aPermissionRefusalCarriesTheRefusedSlug() {
        JsonNode body = api().post(
                        "/aircraft",
                        json(
                                "serial", "5240",
                                "registration", "N908QA",
                                "series", "200",
                                "line_number", 1140,
                                "delivered", "2016-04-12"),
                        Map.of("x-api-key", "mechanic-key-0001"))
                .json();
        assertThat(body.get("type").asText()).isEqualTo("refused");
    }

    @Test
    void everyFieldAClientReadsIsAlwaysPresent() {
        JsonNode body = api().get("/aircraft/9999").json();
        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("type", "kind", "detail", "where", "retryable");
    }

    @Test
    void aProblemCanBeBuiltDirectly() {
        Problem made = new Problem("x", "X", "y");
        assertThat(made.where()).isEmpty();
        assertThat(made.retryable()).isFalse();
    }

    @Test
    void aProblemNeedsAType() {
        assertThatThrownBy(() -> new Problem(null, "X", "y")).isInstanceOf(NullPointerException.class);
    }
}
