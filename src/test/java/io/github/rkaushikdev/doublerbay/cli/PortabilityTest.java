package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.report.Export;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The assumptions that hold on the machine this was written on and nowhere else.
 *
 * <p>Every check in this class exists because the desk runs on Windows and the service runs on
 * Linux, and the defects that follow from that are all silent. Nothing crashes. A card written on
 * the desk with a degree sign in the note reads back as something else on the server, and the only
 * sign of it is a measurement nobody can match to the paper form six months later.
 *
 * <p>The encoding one is not hypothetical. A file read that takes the platform default reads one
 * thing on one machine and another on the other.
 */
class PortabilityTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    /**
     * Characters a structures desk actually types: a degree sign in a bearing, a micro sign in a
     * coating thickness, and a name with an accent in it.
     */
    private static final String AWKWARD = "60\u00b0 bearing, 25\u00b5m primer, raised by j.mu\u00f1oz";

    /** The calls that turn bytes into text or text into bytes, and so need to be told how. */
    private static final List<String> ENCODING_CALLS = List.of(
            "Files.readString(",
            "Files.writeString(",
            "Files.readAllLines(",
            "Files.lines(",
            "Files.newBufferedReader(",
            "Files.newBufferedWriter(",
            "new InputStreamReader(",
            "new OutputStreamWriter(",
            "new FileReader(",
            "new FileWriter(",
            "new Scanner(",
            ".getBytes(");

    @TempDir
    Path tmp;

    private static List<Path> source() {
        try (Stream<Path> walked =
                Files.walk(ROOT.resolve("src").resolve("main").resolve("java"))) {
            return walked.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Every place in a file's text that turns bytes into text or back without naming a charset. */
    static List<String> unnamedEncodings(String where, String text) {
        List<String> offenders = new ArrayList<>();
        for (String call : ENCODING_CALLS) {
            int at = text.indexOf(call);
            while (at >= 0) {
                String arguments = argumentsAfter(text, at + call.length());
                if (!arguments.contains("UTF_8") && !arguments.contains("harset")) {
                    offenders.add(where + ":" + lineOf(text, at));
                }
                at = text.indexOf(call, at + call.length());
            }
        }
        return offenders;
    }

    /** The text of a call's arguments, up to the bracket that closes the one opened just before. */
    private static String argumentsAfter(String text, int from) {
        int depth = 1;
        int at = from;
        while (at < text.length() && depth > 0) {
            char here = text.charAt(at);
            if (here == '(') {
                depth++;
            } else if (here == ')') {
                depth--;
            }
            at++;
        }
        return text.substring(from, Math.max(from, at - 1));
    }

    private static int lineOf(String text, int at) {
        return (int) text.substring(0, at).chars().filter(one -> one == '\n').count() + 1;
    }

    private static Map<String, Object> awkwardCard() {
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
        measured.put("depth", "0.020");
        measured.put("along", "3.000");
        measured.put("around", "2.000");
        measured.put("sharp_bottom", false);
        measured.put("cracked", false);
        card.put("measurements", measured);
        card.put("note", AWKWARD);
        return card;
    }

    @Test
    void thereIsSourceToCheck() {
        assertThat(source()).hasSizeGreaterThan(100);
    }

    /** A read with no encoding named takes whatever the platform says, which differs. */
    @Test
    void everyFileReadInThePackageNamesItsEncoding() {
        List<String> offenders = new ArrayList<>();
        for (Path path : source()) {
            offenders.addAll(unnamedEncodings(ROOT.relativize(path).toString(), read(path)));
        }
        assertThat(offenders).as("reads with no encoding: %s", offenders).isEmpty();
        assertThat(unnamedEncodings("planted", "String text = Files.readString(path);\nbyte[] b = s.getBytes();"))
                .containsExactly("planted:1", "planted:2");
        assertThat(unnamedEncodings(
                        "planted",
                        "Files.readString(Path.of(\"x\"), StandardCharsets.UTF_8);\n"
                                + "new InputStreamReader(System.in, charset);"))
                .isEmpty();
    }

    @Test
    void noSourceFileReachesForAWindowsOnlyModule() {
        List<String> banned = List.of(
                "winreg",
                "msvcrt",
                "cmd.exe",
                "powershell",
                "sun.awt.windows",
                "jna.platform.win32",
                "WindowsRegistry");
        List<String> offenders = source().stream()
                .filter(path -> banned.stream().anyMatch(read(path)::contains))
                .map(path -> ROOT.relativize(path).toString())
                .toList();
        assertThat(offenders).isEmpty();
    }

    @Test
    void noSourceFileCarriesAnAbsoluteWindowsPath() {
        List<String> offenders = source().stream()
                .filter(path -> read(path).contains("C:\\"))
                .map(path -> ROOT.relativize(path).toString())
                .toList();
        assertThat(offenders).isEmpty();
    }

    @Test
    void everySourceFileIsReadableAsUtf8() throws IOException {
        for (Path path : source()) {
            try {
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(path)));
            } catch (CharacterCodingException garbled) {
                throw new AssertionError(ROOT.relativize(path) + " is not UTF-8", garbled);
            }
        }
    }

    @Test
    void aCardDocumentWithAwkwardCharactersReadsBack() {
        Path path = Cli.written(tmp, "card.json", awkwardCard());
        assertThat(Cards.readDocument(path).note()).isEqualTo(AWKWARD);
    }

    @Test
    void aCardRaisedFromSuchADocumentKeepsItsNote() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        Path path = Cli.written(tmp, "card.json", awkwardCard());
        Ran answer = Cli.invoke(database, "cards", "raise", path.toString());
        assertThat(answer.exitCode()).as(answer.output()).isZero();
    }

    @Test
    void aQuestionWithAwkwardCharactersReadsBack() {
        Path path = Cli.writtenText(tmp, "question.txt", AWKWARD);
        assertThat(Ordering.readQuestion(path)).isEqualTo(AWKWARD);
    }

    @Test
    void conditionsWithAwkwardCharactersReadBack() {
        Path path = Cli.writtenText(tmp, "conditions.txt", AWKWARD + "\ninspect at every C check\n");
        assertThat(Ordering.conditionsFrom(path).get(0)).isEqualTo(AWKWARD);
    }

    @Test
    void anOrderCarriesSuchAQuestionThrough() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        FindingId reference = Desk.assessedCard(database);
        Path path = Cli.writtenText(tmp, "question.txt", "why is there a " + AWKWARD + " here and what may we do");
        Ran answer = Cli.invoke(
                database,
                "order",
                "raise",
                reference.toString(),
                "--question",
                path.toString(),
                "--on",
                "2025-03-14",
                "--by",
                "a.okonkwo");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
    }

    @Test
    void theCsvExportWritesTheLineEndingASpreadsheetExpects() {
        assertThat(Export.LINE_END).isEqualTo("\r\n");
        assertThat(Export.asCsv(List.of("a"), List.of(List.of("x")))).endsWith("\r\n");
    }

    /** Without this a checkout on Linux and one on the desk disagree byte for byte. */
    @Test
    void theRepositoryPinsItsLineEndings() {
        assertThat(read(ROOT.resolve(".gitattributes"))).contains("eol=lf");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"build.gradle", "settings.gradle", "gradlew", "src/main/resources/db/migration", ".gitattributes"
            })
    void theFilesCiDependsOnAreAllThere(String name) {
        assertThat(ROOT.resolve(name)).exists();
    }
}
