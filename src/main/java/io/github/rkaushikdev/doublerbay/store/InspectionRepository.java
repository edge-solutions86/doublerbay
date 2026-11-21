package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.EntityManager;

/** The inspection records, read and written through one session. */
public final class InspectionRepository {

    private final EntityManager session;

    InspectionRepository(EntityManager session) {
        this.session = session;
    }

    EntityManager session() {
        return session;
    }
}
