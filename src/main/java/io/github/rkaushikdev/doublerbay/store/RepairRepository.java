package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.EntityManager;

/** The repair records, read and written through one session. */
public final class RepairRepository {

    private final EntityManager session;

    RepairRepository(EntityManager session) {
        this.session = session;
    }

    EntityManager session() {
        return session;
    }
}
