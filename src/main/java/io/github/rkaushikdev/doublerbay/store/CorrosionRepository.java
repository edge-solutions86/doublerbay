package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.EntityManager;

/** The corrosion records, read and written through one session. */
public final class CorrosionRepository {

    private final EntityManager session;

    CorrosionRepository(EntityManager session) {
        this.session = session;
    }

    EntityManager session() {
        return session;
    }
}
