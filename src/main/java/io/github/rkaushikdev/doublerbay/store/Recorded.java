package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** When a row was written, which every table carries for the audit trail, in UTC. */
@MappedSuperclass
public abstract class Recorded {

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** When the row was first written, once it has been. */
    public Optional<LocalDateTime> getRecordedAt() {
        return Optional.ofNullable(recordedAt);
    }

    @PrePersist
    void stamp() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
