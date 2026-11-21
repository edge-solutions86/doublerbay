package io.github.rkaushikdev.doublerbay.ids;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The marks painted on the tail. Reassignable, so never a primary key.
 *
 * <p>A registration identifies an aeroplane today and a different aeroplane in six years, because
 * marks get reassigned when a tail leaves the register. Records that outlive a lease are keyed on
 * the {@link Serial} and displayed by the registration.
 */
public record Registration(String marks) {

    private static final Pattern SHAPE = Pattern.compile("N[1-9][0-9]{0,4}[A-Z]{0,2}");

    /** The marks exactly as the register issues them. */
    public Registration {
        Objects.requireNonNull(marks, "marks");
        if (!SHAPE.matcher(marks).matches()) {
            throw new BadReading(Literal.of(marks) + " is not a registration");
        }
    }

    /** Marks as written on a form: trimmed and read regardless of case. */
    public static Registration parse(String text) {
        return new Registration(Text.upper(Text.strip(text)));
    }

    @Override
    public String toString() {
        return marks;
    }
}
