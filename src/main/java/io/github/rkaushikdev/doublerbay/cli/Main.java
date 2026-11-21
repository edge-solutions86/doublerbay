package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.config.Config;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.service.Readings;
import io.github.rkaushikdev.doublerbay.service.RefusedLine;
import io.github.rkaushikdev.doublerbay.service.Seeded;
import io.github.rkaushikdev.doublerbay.service.Seeding;
import io.github.rkaushikdev.doublerbay.service.Taken;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;

/**
 * The command line, which is what the desk actually uses at half past six.
 *
 * <p>The service has an HTTP surface and nobody on the structures desk is going to open a browser
 * to find out which deferrals expire this week. They are going to type one line into a terminal on
 * the way to a meeting, so the commands are short, the output is plain columns, and everything that
 * takes a date takes it in the same format.
 *
 * <p>Every command opens its own unit of work and closes it. A long running session holding a
 * transaction open across a coffee break is how a maintenance system ends up with a locked row
 * nobody can explain.
 *
 * <p>The exit code is the answer: {@link #OK}, {@link #REFUSED} for a refusal the desk made, and
 * {@link #MISUSED} for a command line written wrong. No command but {@code serve} starts the HTTP
 * service; the others open the database they were pointed at, or the one an embedding caller
 * handed in, and nothing else.
 */
@Command(
        name = "doublerbay",
        customSynopsis = "doublerbay [OPTIONS] COMMAND [ARGS]...",
        description = "Skin damage disposition for the structures desk.",
        subcommands = {
            Assessing.AssessCard.class,
            Assessing.AssessTail.class,
            Cards.Group.class,
            Checking.Group.class,
            Serving.ShowContract.class,
            Corroding.Group.class,
            Desk.Defer.class,
            Main.Fleet.class,
            Serving.ShowGates.class,
            Main.Init.class,
            Ordering.Group.class,
            Repairing.Group.class,
            Desk.Report.class,
            Serving.ShowRoutes.class,
            Main.Seed.class,
            Serving.Serve.class,
            Serving.ShowSettings.class,
            Serving.ShowVariables.class,
        })
public final class Main {

    /** It worked. */
    public static final int OK = 0;

    /** The desk refused, or a check the command runs did not pass. */
    public static final int REFUSED = 1;

    /** The command line was written wrong. */
    public static final int MISUSED = 2;

    /** The variable the database url is read from when {@code --database} is left off. */
    public static final String DATABASE_VARIABLE = Config.named("database");

    private static final String HELP = "Show this message and exit.";

    private final Terminal terminal;
    private final Database handed;
    private Database opened;

    @Option(names = "--database", defaultValue = Database.IN_MEMORY, showDefaultValue = Visibility.ALWAYS)
    private String database;

    private Main(Terminal terminal, Database handed) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.handed = handed;
    }

    /** The console script. */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /**
     * Run one command line against the database {@code --database} names, turning a refusal into a
     * message rather than a stack trace.
     *
     * @return the exit code
     */
    public static int run(Terminal terminal, String... args) {
        return refusedAsAMessage(terminal, () -> execute(terminal, args));
    }

    /**
     * Run one command line against a database the caller already holds, turning a refusal into a
     * message rather than a stack trace. The database is left open: it is the caller's.
     *
     * @return the exit code
     */
    public static int run(Terminal terminal, Database database, String... args) {
        return refusedAsAMessage(terminal, () -> execute(terminal, database, args));
    }

    /**
     * Run one command line against the database {@code --database} names. A usage error is printed
     * and answered with {@link #MISUSED}; a refusal, or anything else a command throws, is thrown on.
     *
     * @return the exit code
     */
    public static int execute(Terminal terminal, String... args) {
        return new Main(terminal, null).dispatch(args);
    }

    /**
     * Run one command line against a database the caller already holds, which is how every command
     * in one session runs against one in-memory database rather than a new empty one each. A usage
     * error is printed and answered with {@link #MISUSED}; a refusal, or anything else a command
     * throws, is thrown on.
     *
     * @return the exit code
     */
    public static int execute(Terminal terminal, Database database, String... args) {
        return new Main(terminal, Objects.requireNonNull(database, "database")).dispatch(args);
    }

    private interface Execution {
        int run();
    }

    private static int refusedAsAMessage(Terminal terminal, Execution execution) {
        try {
            return execution.run();
        } catch (DoublerbayError refusal) {
            terminal.err().print("refused: " + refusal.getMessage() + "\n");
            terminal.err().flush();
            return REFUSED;
        }
    }

    /** The command tree, with every command taking {@code --help} and nothing else picocli would add. */
    CommandLine commandLine() {
        CommandLine line = new CommandLine(this);
        CommandSpec root = line.getCommandSpec();
        line.setExpandAtFiles(false)
                .setOverwrittenOptionsAllowed(true)
                .registerConverter(Integer.class, new Arguments.WholeNumber())
                .registerConverter(int.class, new Arguments.WholeNumber())
                .registerConverter(Path.class, new Arguments.AnyPath())
                .setDefaultValueProvider(argument -> argument instanceof OptionSpec option
                                && option.command() == root
                                && option.longestName().equals("--database")
                        ? terminal.environment().get(DATABASE_VARIABLE)
                        : null)
                .setOut(terminal.out())
                .setErr(terminal.err());
        withHelp(line);
        return line;
    }

    private static void withHelp(CommandLine line) {
        CommandSpec spec = line.getCommandSpec();
        spec.usageMessage().sortOptions(false).sortSynopsis(false);
        spec.addOption(
                OptionSpec.builder("--help").usageHelp(true).description(HELP).build());
        line.getSubcommands().values().forEach(Main::withHelp);
    }

    private int dispatch(String... args) {
        try {
            return dispatchOn(commandLine(), args);
        } finally {
            if (opened != null) {
                opened.close();
            }
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    private int dispatchOn(CommandLine line, String... args) {
        ParseResult parsed;
        try {
            parsed = line.parseArgs(args);
        } catch (ParameterException misread) {
            return misused(misread.getCommandLine(), misread.getMessage());
        }
        Integer helped = CommandLine.executeHelpRequest(parsed);
        if (helped != null) {
            return helped;
        }
        ParseResult reached = parsed;
        while (reached.subcommand() != null) {
            reached = reached.subcommand();
        }
        CommandLine leaf = reached.commandSpec().commandLine();
        if (!(leaf.getCommand() instanceof Step step)) {
            if (reached.matchedArgs().isEmpty()) {
                leaf.usage(terminal.out());
                return OK;
            }
            return misused(leaf, "Missing command.");
        }
        try {
            return step.callFrom(this);
        } catch (ParameterException misread) {
            return misused(misread.getCommandLine(), misread.getMessage());
        } catch (Misused wrong) {
            return misused(leaf, wrong.said());
        }
    }

    /** Say how the command is used, where to find its help, and what was wrong. */
    private int misused(CommandLine where, String what) {
        CommandSpec spec = where.getCommandSpec();
        String[] custom = spec.usageMessage().customSynopsis();
        String synopsis =
                custom.length > 0 ? custom[0] : where.getHelp().synopsis(0).strip();
        terminal.err().print("Usage: " + synopsis + "\n");
        terminal.err().print("Try '" + spec.qualifiedName() + " --help' for help.\n\n");
        terminal.err().print("Error: " + what + "\n");
        return MISUSED;
    }

    /** The terminal this command line talks to. */
    Terminal terminal() {
        return terminal;
    }

    /** Print one line, or several separated by line breaks, to standard output. */
    void say(String text) {
        terminal.out().print(text + "\n");
    }

    /**
     * The database every command works against: the one handed in, or the one {@code --database}
     * (or the environment) names, opened the first time a command asks for it.
     */
    Database database() {
        if (handed != null) {
            return handed;
        }
        if (opened == null) {
            opened = Database.open(database);
        }
        return opened;
    }

    /** Build the schema in an empty database. */
    @Command(
            name = "init",
            customSynopsis = "doublerbay init [OPTIONS]",
            description = "Build the schema in an empty database.")
    static final class Init extends Step {
        @Override
        public Integer call() {
            main().database().buildSchema();
            say("built " + Database.tableNames().size() + " tables");
            return OK;
        }
    }

    /** The register: tails, their counters and what they are flying at. */
    @Command(
            name = "fleet",
            customSynopsis = "doublerbay fleet [OPTIONS] COMMAND [ARGS]...",
            description = "The register: tails, their counters and what they are flying at.",
            subcommands = {
                FleetEnrol.class,
                FleetImport.class,
                FleetList.class,
                FleetPost.class,
                FleetShow.class,
            })
    static final class Fleet {}

    /** Every tail on the register. */
    @Command(
            name = "list",
            customSynopsis = "doublerbay fleet list [OPTIONS]",
            description = "Every tail on the register.")
    static final class FleetList extends Step {

        @Option(names = "--on", description = "only what was in service on this day")
        String onDay;

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("serial", "marks", "series", "delivered"));
            inWork(unit -> {
                List<Aircraft> held = given(onDay)
                        ? unit.aircraft().inServiceOn(Output.asDate(onDay))
                        : unit.aircraft().all();
                for (Aircraft one : held) {
                    rows.add(List.of(
                            one.serial().number(),
                            one.registration().toString(),
                            one.series().toString(),
                            one.delivered().toString()));
                }
            });
            say(Output.columns(rows));
            return OK;
        }
    }

    /** One tail, with its latest reading and what it is flying at. */
    @Command(
            name = "show",
            customSynopsis = "doublerbay fleet show [OPTIONS] SERIAL",
            description = "One tail, with its latest reading and what it is flying at.")
    static final class FleetShow extends Step {

        @Parameters(index = "0", paramLabel = "SERIAL")
        String serial;

        @Override
        public Integer call() {
            List<List<String>> rows = work(unit -> {
                Serial held = new Serial(serial);
                Aircraft aircraft = unit.aircraft().get(held);
                UtilisationLog log = unit.aircraft().logFor(held);
                List<List<String>> laid = new ArrayList<>();
                laid.add(List.of("tail", aircraft.toString()));
                laid.add(List.of("line number", String.valueOf(aircraft.lineNumber())));
                laid.add(List.of("delivered", aircraft.delivered().toString()));
                laid.add(List.of("last read", log.latest().taken().toString()));
                laid.add(List.of("flown", log.latest().usage().toString()));
                try {
                    Forecast forecast = Forecast.fromLog(log);
                    laid.add(List.of(
                            "flying at",
                            Decimals.text(forecast.cyclesADay()) + " FC and " + Decimals.text(forecast.hoursADay())
                                    + " FH a day"));
                } catch (DoublerbayError tooFew) {
                    laid.add(List.of("flying at", "not enough readings to say"));
                }
                return laid;
            });
            say(Output.columns(rows));
            return OK;
        }
    }

    /** Put a tail on the register and open its log at delivery. */
    @Command(
            name = "enrol",
            customSynopsis = "doublerbay fleet enrol [OPTIONS] SERIAL MARKS",
            description = "Put a tail on the register and open its log at delivery.")
    static final class FleetEnrol extends Step {

        @Parameters(index = "0", paramLabel = "SERIAL")
        String serial;

        @Parameters(index = "1", paramLabel = "MARKS")
        String marks;

        @Option(names = "--series", defaultValue = "200", showDefaultValue = Visibility.ALWAYS)
        String series;

        @Option(names = "--line-number", required = true)
        int lineNumber;

        @Option(names = "--delivered", required = true, description = "yyyy-mm-dd")
        String delivered;

        @Override
        public Integer call() {
            Aircraft aircraft = new Aircraft(
                    new Serial(serial),
                    Registration.parse(marks),
                    Series.fromValue(series),
                    lineNumber,
                    Output.asDate(delivered));
            inWork(unit -> {
                unit.aircraft().add(aircraft);
                unit.aircraft().openLog(UtilisationLog.opened(aircraft.serial(), aircraft.delivered()));
            });
            say("enrolled " + aircraft);
            return OK;
        }
    }

    /** Post a counter reading against a tail. */
    @Command(
            name = "post",
            customSynopsis = "doublerbay fleet post [OPTIONS] SERIAL TAKEN",
            description = "Post a counter reading against a tail.")
    static final class FleetPost extends Step {

        @Parameters(index = "0", paramLabel = "SERIAL")
        String serial;

        @Parameters(index = "1", paramLabel = "TAKEN")
        String taken;

        @Option(names = "--cycles", required = true)
        int cycles;

        @Option(names = "--hours", required = true, description = "decimal flight hours")
        String hours;

        @Override
        public Integer call() {
            Snapshot reading = new Snapshot(Output.asDate(taken), new Usage(new Cycles(cycles), Hours.ofHours(hours)));
            inWork(unit -> unit.aircraft().postReading(new Serial(serial), reading));
            say("posted " + reading);
            return OK;
        }
    }

    /** Post a night of counter readings from the maintenance system export. */
    @Command(
            name = "import",
            customSynopsis = "doublerbay fleet import [OPTIONS] DOCUMENT",
            description = "Post a night of counter readings from the maintenance system export.")
    static final class FleetImport extends Step {

        @Parameters(index = "0", paramLabel = "DOCUMENT", converter = Arguments.ExistingFile.class)
        Path document;

        @Override
        public Integer call() {
            Taken taken = work(unit -> Readings.postAll(unit, Texts.read(document)));
            say(taken.toString());
            for (RefusedLine one : taken.refused()) {
                say("  " + one);
            }
            return OK;
        }
    }

    /** Put a small demonstration fleet on an empty database. */
    @Command(
            name = "seed",
            customSynopsis = "doublerbay seed [OPTIONS]",
            description = "Put a small demonstration fleet on an empty database.")
    static final class Seed extends Step {

        @Option(names = "--on", description = "the day the fleet is seeded as of")
        String onDay;

        @Override
        public Integer call() {
            Seeded answer = work(unit -> Seeding.seed(unit, given(onDay) ? Output.asDate(onDay) : Seeding.LATEST));
            say("seeded " + answer);
            return OK;
        }
    }
}
