package io.github.rkaushikdev.doublerbay.tooling;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.List;
import java.util.Objects;

/** One check, its command, and why it is worth the seconds it takes. */
public record Gate(String name, List<String> command, String why) {

    /** A gate with a name and a command to run. */
    public Gate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(why, "why");
        command = List.copyOf(command);
        if (Text.isBlank(name)) {
            throw new BadReading("a gate has a name");
        }
        if (command.isEmpty()) {
            throw new BadReading(name + " has no command to run");
        }
    }

    /** The command as it would be typed, its words separated by spaces. */
    public String written() {
        return String.join(" ", command);
    }

    /** {@code format: ./gradlew spotlessCheck}. */
    @Override
    public String toString() {
        return name + ": " + written();
    }
}
