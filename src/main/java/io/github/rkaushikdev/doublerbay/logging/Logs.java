package io.github.rkaushikdev.doublerbay.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import io.github.rkaushikdev.doublerbay.util.Iso;
import io.github.rkaushikdev.doublerbay.util.JsonText;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;

/**
 * What the service writes down about itself, and what it deliberately does not.
 *
 * <p>Two things get logged and nothing else does. Every refusal, because a refusal is the service
 * telling somebody no and the desk will eventually want to know how often and to whom. And every
 * write that changes what an aeroplane is allowed to do, because those are the ones an audit asks
 * about. Reads are not logged: a read of the due list happens forty times a morning.
 *
 * <p>Nothing here writes a measurement, a serial or a key. The serial is the one that looks
 * harmless and is not: a log line naming the aeroplane and the disposition is the finding, sitting
 * in a file with weaker access rules than the database it came out of.
 */
public final class Logs {

    /** The name of the one logger this package writes to. */
    public static final String NAME = "doublerbay";

    /** What a line can carry. Anything not on this list does not go in a log. */
    public static final Set<String> ALLOWED = Set.of("event", "reference", "kind", "role", "outcome", "detail");

    /** What must never go in one, even if somebody passes it. */
    public static final Set<String> NEVER = Set.of("serial", "key", "holder", "measurements", "depth", "registration");

    private static final String CONSOLE = "doublerbay-console";

    private Logs() {}

    /**
     * Drop anything this service will not put in a log file, keeping the order of what is left.
     *
     * <p>A caller passing a serial is not doing anything malicious. They are debugging, and the
     * line they add outlives the afternoon they added it in.
     */
    public static Map<String, String> scrubbed(Map<String, String> fields) {
        Map<String, String> kept = new LinkedHashMap<>();
        fields.forEach((name, value) -> {
            if (ALLOWED.contains(name)) {
                kept.put(name, value);
            }
        });
        return Collections.unmodifiableMap(kept);
    }

    /** One line as JSON with the moment it was written, which is what a log shipper wants. */
    public static String asJson(Line line, OffsetDateTime at) {
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("at", Iso.of(at));
        written.putAll(scrubbed(line.fields()));
        return JsonText.object(written);
    }

    /** One line as JSON, stamped now in UTC. */
    public static String asJson(Line line) {
        return asJson(line, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** The one logger this package writes to. */
    public static Logger logger() {
        return (Logger) LoggerFactory.getLogger(NAME);
    }

    /** Write one line, at info because none of this is a problem by itself. */
    public static void write(Line line, OffsetDateTime at) {
        logger().info(asJson(line, at));
    }

    /** Write one line, stamped now. */
    public static void write(Line line) {
        logger().info(asJson(line));
    }

    /** The line for a request the service said no to. */
    public static Line refused(String reference, String kind, String why, String role) {
        return Line.of("refused", reference, kind, role, "no", why);
    }

    /** The line for a request the service said no to, made under no known role. */
    public static Line refused(String reference, String kind, String why) {
        return refused(reference, kind, why, null);
    }

    /** The line for a write that changes what an aeroplane may do. */
    public static Line decided(String reference, String kind, String role) {
        return Line.of("decided", reference, kind, role, "yes", null);
    }

    /** Attach one handler at info, once. */
    public static Logger configure() {
        return configure(Level.INFO);
    }

    /**
     * Attach one handler, once, without touching the root logger.
     *
     * <p>Adding a handler to root would put this service's lines through whatever the host
     * application has configured, and take that application's lines through this one. A named
     * logger that does not pass its lines up keeps both.
     */
    public static Logger configure(Level level) {
        Logger held = logger();
        held.setLevel(level);
        held.setAdditive(false);
        if (!held.iteratorForAppenders().hasNext()) {
            LoggerContext context = held.getLoggerContext();
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%msg%n");
            encoder.start();
            ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
            console.setContext(context);
            console.setName(CONSOLE);
            console.setTarget("System.err");
            console.setEncoder(encoder);
            console.start();
            held.addAppender(console);
        }
        return held;
    }
}
