package io.github.rkaushikdev.doublerbay.store;

import jakarta.persistence.EntityManager;

/** The deferral records, read and written through one session. */
public final class DeferralRepository {

    private final EntityManager session;

    DeferralRepository(EntityManager session) {
        this.session = session;
    }

    EntityManager session() {
        return session;
    }
}
