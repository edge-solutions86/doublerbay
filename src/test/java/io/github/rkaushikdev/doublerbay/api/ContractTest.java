package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

class ContractTest {

    private static ConfigurableApplicationContext app() {
        return Api.clientFor(Desk.newDatabase()).app();
    }

    @Test
    void theSurfaceListsItsRoutes() {
        assertThat(Contract.endpoints(app())).hasSizeGreaterThan(40);
    }

    @Test
    void everyRouteCarriesAPathAndAMethod() {
        for (Endpoint one : Contract.endpoints(app())) {
            assertThat(one.path()).startsWith("/");
            assertThat(one.method()).isNotEmpty();
        }
    }

    @Test
    void theRoutesComeBackInPathOrder() {
        List<String> paths =
                Contract.endpoints(app()).stream().map(Endpoint::path).toList();
        assertThat(paths).isSorted();
    }

    @Test
    void aPostIsAWrite() {
        assertThat(new Endpoint("/x", "POST", "x", "x", true, List.of()).writes())
                .isTrue();
    }

    @Test
    void aGetIsNotAWrite() {
        assertThat(new Endpoint("/x", "GET", "x", "x", true, List.of()).writes())
                .isFalse();
    }

    @Test
    void theWritingMethodsAreTheOnesThatChangeSomething() {
        assertThat(Contract.WRITING).isEqualTo(Set.of("POST", "PUT", "PATCH", "DELETE"));
    }

    @Test
    void theSurfaceHasWritesOnIt() {
        assertThat(Contract.writes(app())).hasSizeGreaterThan(10);
    }

    @Test
    void noWriteOnThisServiceIsUnguarded() {
        List<Endpoint> openWrites = Contract.unguardedWrites(app());
        assertThat(openWrites)
                .as("unguarded: %s", openWrites.stream().map(Endpoint::toString).toList())
                .isEmpty();
    }

    @Test
    void everyRouteIsDocumented() {
        assertThat(Contract.undocumented(app())).isEmpty();
    }

    @Test
    void theHealthRouteIsDeliberatelyOpen() {
        assertThat(Contract.UNGUARDED).containsKey("/health");
    }

    @Test
    void anOpenRouteReadsBackAsOpen() {
        assertThat(new Endpoint("/health", "GET", "health", "x", false, List.of()))
                .hasToString("GET /health (open)");
    }

    @Test
    void aGuardedRouteReadsBackAsGuarded() {
        assertThat(new Endpoint("/x", "POST", "x", "x", true, List.of()).toString())
                .contains("(guarded)");
    }

    @Test
    void aRouteWithNoSummaryIsUndocumented() {
        assertThat(new Endpoint("/x", "GET", "x", "   ", true, List.of()).isDocumented())
                .isFalse();
    }

    @Test
    void aWriteOnTheOpenListNeedsNoGuard() {
        assertThat(new Endpoint("/health", "POST", "x", "x", false, List.of()).needsAGuard())
                .isFalse();
    }

    @Test
    void aWriteOffTheListNeedsAGuard() {
        assertThat(new Endpoint("/deferrals", "POST", "x", "x", false, List.of()).needsAGuard())
                .isTrue();
    }

    @Test
    void theSurfaceGroupsItsRoutesUnderTags() {
        List<String> every = Contract.tags(app());
        assertThat(every).contains("findings");
        assertThat(every).contains("reports");
        assertThat(every).hasSizeGreaterThan(8);
    }

    @Test
    void theContractIsJsonThatParses() throws Exception {
        assertThat(new ObjectMapper()
                        .readTree(Contract.asDocument(app()))
                        .get("info")
                        .get("title")
                        .asText())
                .isEqualTo("doublerbay");
    }

    @Test
    void theContractCarriesEveryPath() throws Exception {
        JsonNode document = new ObjectMapper().readTree(Contract.asDocument(app()));
        assertThat(document.get("paths").has("/deferrals")).isTrue();
        assertThat(document.get("paths").has("/reports/due")).isTrue();
    }

    @Test
    void theContractIsStableBetweenTwoBuilds() {
        assertThat(Contract.asDocument(app())).isEqualTo(Contract.asDocument(app()));
    }

    @Test
    void theRoutesCommandListsThem() {
        Ran answer = Cli.invoke("routes");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("/deferrals");
        assertThat(answer.output()).contains("guarded");
    }

    @Test
    void theRoutesCommandCanShowOnlyWrites() {
        Ran answer = Cli.invoke("routes", "--writes-only");
        assertThat(answer.output()).contains("POST");
        assertThat(answer.output()).doesNotContain("GET ");
    }

    @Test
    void theRoutesCommandReportsNoUnguardedWrites() {
        Ran answer = Cli.invoke("routes");
        assertThat(answer.output()).doesNotContain("nothing is guarding");
        assertThat(answer.output()).doesNotContain("nothing guards");
        assertThat(answer.exitCode()).isZero();
    }

    @Test
    void theContractCommandPrintsTheDocument() throws Exception {
        Ran answer = Cli.invoke("contract");
        assertThat(new ObjectMapper().readTree(answer.output()).get("openapi").asText())
                .startsWith("3.");
    }

    @Test
    void theContractCommandWritesAFile(@TempDir Path tmp) throws Exception {
        Path path = tmp.resolve("openapi.json");
        Ran answer = Cli.invoke("contract", "--to", path.toString());
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("wrote");
        assertThat(new ObjectMapper()
                        .readTree(Files.readString(path, StandardCharsets.UTF_8))
                        .get("info")
                        .get("title")
                        .asText())
                .isEqualTo("doublerbay");
    }
}
