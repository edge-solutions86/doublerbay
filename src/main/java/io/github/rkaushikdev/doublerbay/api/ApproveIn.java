package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An approval as it comes back from the manufacturer.
 *
 * @param on the day it came back
 * @param by the engineer recording it
 * @param sheet the approval sheet number
 * @param category the category it puts the repair in, as the desk writes it
 * @param conditions what the approval holds the operator to, none when left out
 * @param validUntil the last day it holds, when it runs out
 */
public record ApproveIn(
        @NotNull LocalDate on,
        @NotNull String by,
        @NotNull String sheet,
        @NotNull String category,
        List<@NotNull String> conditions,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<LocalDate> validUntil) {

    public ApproveIn {
        conditions = conditions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(conditions));
        validUntil = validUntil == null ? Optional.empty() : validUntil;
    }

    /** The approval, refusing a category or a sheet number the desk would not file. */
    public RepairApproval toDomain() {
        return new RepairApproval(
                sheet,
                Schemas.readEnum(RepairCategory.values(), RepairCategory::value, category, "repair category"),
                List.copyOf(conditions),
                validUntil);
    }
}
