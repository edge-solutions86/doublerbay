package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliCardsTest {

    @TempDir
    Path tmp;

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        Database database = Desk.newDatabase();
        invoke(database, "fleet", "enrol", "5127", "N412VD", "--line-number", "1140", "--delivered", "2016-04-12");
        return database;
    }

    private static Map<String, Object> dentDocument() {
        Map<String, Object> card = Cards.templateFor(DamageKind.DENT, "fuselage");
        card.put("serial", "5127");
        card.put("found_on", "2025-03-14");
        card.put("origin", "impact");
        card.put("source", "walkaround");
        card.put("at_usage", Map.of("cycles", 13800, "hours", "24200.0"));
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("structure", "fuselage");
        where.put("frame", 15);
        where.put("aft_of_frame", "10.000");
        where.put("stringer", 6);
        where.put("below_stringer", "0.000");
        where.put("side", "L");
        card.put("fuselage", where);
        Map<String, Object> measured = new LinkedHashMap<>();
        measured.put("depth", "0.030");
        measured.put("along", "1.500");
        measured.put("around", "1.200");
        measured.put("sharp_bottom", false);
        measured.put("cracked", false);
        card.put("measurements", measured);
        return card;
    }

    private String written(Map<String, Object> card) {
        return written(card, "card.json");
    }

    private String written(Map<String, Object> card, String name) {
        return Cli.written(tmp, name, card).toString();
    }

    private String raised(Database database) {
        Ran answer = invoke(database, "cards", "raise", written(dentDocument()));
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        return answer.lastWord();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTemplateCarriesOnlyWhatTheKindMeasures() {
        Map<String, Object> dent = Cards.templateFor(DamageKind.DENT, "fuselage");
        assertThat(((Map<String, Object>) dent.get("measurements")).keySet())
                .containsExactlyInAnyOrder("depth", "along", "around", "sharp_bottom", "cracked");
        Map<String, Object> crack = Cards.templateFor(DamageKind.CRACK, "wing");
        assertThat((Map<String, Object>) crack.get("measurements")).containsKey("tips_found");
        assertThat((Map<String, Object>) crack.get("measurements")).doesNotContainKey("depth");
    }

    @Test
    void aTemplatePutsTheLocationOnTheStructureAskedFor() {
        assertThat(Cards.templateFor(DamageKind.CRACK, "wing")).containsKey("wing");
        assertThat(Cards.templateFor(DamageKind.CRACK, "wing")).doesNotContainKey("fuselage");
    }

    @Test
    void aKindWithNoTemplateSaysSo() {
        Ran answer = Cli.invoke("cards", "template", "disbond");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("no card template");
    }

    @Test
    void aStructureThatIsNotOneIsRefused() {
        Ran answer = Cli.invoke("cards", "template", "dent", "--structure", "tail");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void thePrintedTemplateIsJsonThatParses() throws Exception {
        Ran answer = Cli.invoke("cards", "template", "dent");
        JsonNode read = new ObjectMapper().readTree(answer.output());
        assertThat(read.get("kind").asText()).isEqualTo("dent");
        assertThat(answer.output())
                .startsWith("{\n  \"serial\": \"\",\n  \"kind\": \"dent\",\n")
                .contains("  \"at_usage\": {\n    \"cycles\": 0,\n    \"hours\": \"0.0\"\n  },\n")
                .contains("    \"sharp_bottom\": false,\n")
                .endsWith("  \"note\": \"\"\n}\n");
    }

    @Test
    void aDocumentThatIsNotJsonIsRefusedByName() {
        Path path = Cli.writtenText(tmp, "card.json", "{not json");
        assertThatThrownBy(() -> Cards.readDocument(path))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("readable json");
    }

    @Test
    void aDocumentThatIsAListIsRefused() {
        Path path = Cli.writtenText(tmp, "card.json", "[]");
        assertThatThrownBy(() -> Cards.readDocument(path))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("it is a list");
    }

    @Test
    void aCardIsRaisedAndGetsAReference() {
        Database database = stocked();
        String reference = raised(database);
        assertThat(reference).startsWith("DR-2025-");
    }

    @Test
    void twoCardsGetDifferentReferences() {
        Database database = stocked();
        String first = raised(database);
        String second = invoke(database, "cards", "raise", written(dentDocument(), "second.json"))
                .lastWord();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aRaisedCardShowsItsMeasurementsAsWritten() {
        Database database = stocked();
        String reference = raised(database);
        String shown = invoke(database, "cards", "show", reference).output();
        assertThat(shown).contains("0.030");
        assertThat(shown).contains("dent");
        assertThat(shown).contains("FR15");
        assertThat(shown).containsPattern("cracked +False").doesNotContain("false");
    }

    @Test
    void aCardThatIsNotThereIsRefused() {
        Ran answer = invoke(stocked(), "cards", "show", "DR-2025-0099");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aReferenceThatIsNotAReferenceIsRefused() {
        Ran answer = invoke(stocked(), "cards", "show", "nonsense");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theCardsOnATailAreListed() {
        Database database = stocked();
        String reference = raised(database);
        String listed = invoke(database, "cards", "list", "--tail", "5127").output();
        assertThat(listed).contains(reference);
        assertThat(listed).contains("dent");
    }

    @Test
    void aTailWithNoCardsListsOnlyTheHeading() {
        String listed = invoke(stocked(), "cards", "list", "--tail", "5127").output();
        assertThat(listed.strip()).isEqualTo("reference  kind  found on  where");
    }

    @Test
    void aHistoryStartsAtOneRevision() {
        Database database = stocked();
        String reference = raised(database);
        String history = invoke(database, "cards", "history", reference).output();
        assertThat(history.strip().split("\n", -1)).hasSize(2);
    }

    @Test
    void columnsPadAShortRowOut() {
        assertThat(Output.columns(List.of(List.of("a", "b"), List.of("c"))).split("\n", -1)[1])
                .isEqualTo("c");
    }

    @Test
    void anEmptyReportHasASentenceForIt() {
        assertThat(Output.sayNothingFound("open on 5127")).isEqualTo("nothing open on 5127");
    }

    @Test
    void aDocumentThatIsNotThereIsAUsageError() {
        Ran answer =
                invoke(stocked(), "cards", "raise", tmp.resolve("missing.json").toString());
        assertThat(answer.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(answer.errors()).contains("does not exist.");
        Ran directory = invoke(stocked(), "cards", "raise", tmp.toString());
        assertThat(directory.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(directory.errors()).contains("is a directory.");
    }
}
