package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What comes back when the manufacturer says yes: a repair approval sheet, the category it puts the
 * repair in, usually a set of conditions, and the date it stops being valid if it has one.
 */
public record RepairApproval(
        String sheet, RepairCategory category, List<String> conditions, Optional<LocalDate> validUntil) {

    private static final Pattern SHEET = Pattern.compile("RAS-[0-9]{4}-[0-9]{4}");

    /** An approval whose sheet number looks like one, read regardless of case and surrounding white space. */
    public RepairApproval {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(category, "category");
        conditions = List.copyOf(conditions);
        Objects.requireNonNull(validUntil, "validUntil");
        if (!SHEET.matcher(Text.upper(Text.strip(sheet))).matches()) {
            throw new BadReading(Literal.of(sheet) + " is not a repair approval sheet number");
        }
    }

    /** An approval with no conditions and no expiry. */
    public RepairApproval(String sheet, RepairCategory category) {
        this(sheet, category, List.of(), Optional.empty());
    }

    /** An approval with conditions and no expiry. */
    public RepairApproval(String sheet, RepairCategory category, List<String> conditions) {
        this(sheet, category, conditions, Optional.empty());
    }

    /** An approval from what was to hand; a null expiry is an approval that never runs out. */
    public static RepairApproval of(
            String sheet, RepairCategory category, List<String> conditions, LocalDate validUntil) {
        return new RepairApproval(sheet, category, conditions, Optional.ofNullable(validUntil));
    }

    /** Whether the approval still holds on a day; the expiry day itself is inside it. */
    public boolean isValidOn(LocalDate day) {
        return validUntil.isEmpty() || !day.isAfter(validUntil.get());
    }

    /** {@code RAS-2025-0007 category B, expires 2027-01-01}, the last part only when it does. */
    @Override
    public String toString() {
        String tail = validUntil.map(day -> ", expires " + day).orElse("");
        return sheet + " " + category + tail;
    }
}
