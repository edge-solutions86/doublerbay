package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.EntityManager;

/** The order records, read and written through one session. */
public final class OrderRepository {

    private final EntityManager session;

    OrderRepository(EntityManager session) {
        this.session = session;
    }

    EntityManager session() {
        return session;
    }
}
