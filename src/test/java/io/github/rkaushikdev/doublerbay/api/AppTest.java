package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Databases;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AppTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);

    private static Deps deps() {
        return deps(DAY);
    }

    private static Deps deps(LocalDate today) {
        return Deps.of(Databases.fresh(), Book.v320(), new TypeCatalogue(), today, null);
    }

    /** A route a test adds to the application, throwing whatever the test hands it. */
    @RestController
    static class Boom {

        private static volatile DoublerbayError next;

        @GetMapping("/boom")
        void boom() {
            throw next;
        }
    }

    private static Client appWithARoute(DoublerbayError error) {
        Boom.next = error;
        return Client.over(Api.app(deps(), Boom.class));
    }

    @Test
    void theServiceAnswersThatItIsUp() {
        JsonNode body = Client.over(Api.app(deps())).get("/health").json();
        assertThat(body.get("service").asText()).isEqualTo("doublerbay");
        assertThat(body.get("chapters").asText()).contains("53");
        assertThat(body.get("chapters").asText()).contains("57");
    }

    @Test
    void theDefaultApplicationHasTheV320BookOnTheShelf() {
        try (ConfigurableApplicationContext built = DoublerbayApi.defaultApp(Databases.fresh(), "--server.port=0")) {
            assertThat(built).isInstanceOf(ConfigurableApplicationContext.class);
            assertThat(built.getBean(Deps.class).book().size()).isEqualTo(2);
        }
    }

    @Test
    void aBadReadingComesBackAsUnprocessable() {
        Client.Answer answer = appWithARoute(new BadReading("0.0405 in is finer than a thousandth"))
                .get("/boom");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("kind").asText()).isEqualTo("BadReading");
        assertThat(answer.json().get("detail").asText()).contains("finer than a thousandth");
    }

    @Test
    void aLookupMissComesBackAsNotFound() {
        Client.Answer answer =
                appWithARoute(new NotOnFile("MSN 5127 is not on the register")).get("/boom");
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("kind").asText()).isEqualTo("NotOnFile");
    }

    @Test
    void aMoveTheLifecycleRefusesComesBackAsAConflict() {
        Client client = appWithARoute(new OutOfSequence("cannot go from raised to closed"));
        assertThat(client.get("/boom").status()).isEqualTo(409);
    }

    @Test
    void aRefusalComesBackAsUnprocessable() {
        Client.Answer answer =
                appWithARoute(new Refused("no page", "the book has run out")).get("/boom");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).isEqualTo("no page: the book has run out");
    }

    @Test
    void aRefusalNobodyMappedComesBackAsAServerError() {
        class Odd extends DoublerbayError {
            Odd(String message) {
                super(message);
            }
        }

        Client client = appWithARoute(new Odd("something nobody expected"));
        assertThat(client.get("/boom").status()).isEqualTo(500);
    }

    @Test
    void theStatusTableCoversEveryRefusalThePackageRaises() {
        assertThat(Problem.statusFor(new BadReading("x"))).isEqualTo(422);
        assertThat(Problem.statusFor(new NotOnFile("x"))).isEqualTo(404);
        assertThat(Problem.statusFor(new OutOfSequence("x"))).isEqualTo(409);
        assertThat(Problem.statusFor(new Refused("x"))).isEqualTo(422);
    }

    @Test
    void everyFailureComesBackInTheSameShape() {
        Problem problem = Problem.of(new NotOnFile("nothing here"));
        assertThat(problem).isInstanceOf(Problem.class);
        List<String> fields = new ArrayList<>();
        Wire.mapper().valueToTree(problem).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("type", "kind", "detail", "where", "retryable");
    }

    @Test
    void theDayARequestIsAnsweredAsOfCanBePinned() {
        assertThat(deps().on()).isEqualTo(DAY);
    }

    @Test
    void withNoDayPinnedTheServiceAnswersAsOfToday() {
        assertThat(deps(null).on()).isEqualTo(LocalDate.now());
    }

    @Test
    void theDependenciesAreHandedInRatherThanImported() {
        ConfigurableApplicationContext built = Api.app(deps());
        TypeCatalogue catalogue = built.getBean(Deps.class).catalogue();
        assertThat(catalogue.series()).isNotEmpty();
        assertThat(catalogue.series()).allMatch(catalogue::carries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health"})
    void theDocumentedRoutesAreReachable(String path) {
        assertThat(Client.over(Api.app(deps())).get(path).status()).isEqualTo(200);
    }
}
