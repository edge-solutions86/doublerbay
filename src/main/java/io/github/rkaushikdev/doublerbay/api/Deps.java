package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Everything a route needs that is not in the request.
 *
 * <p>The service never builds its own database, book or catalogue. Whoever starts it opens them and
 * hands them in, which is how a test runs one service per database and how the command line serves
 * the database it was pointed at.
 *
 * @param database the register every unit of work opens against
 * @param book the manual on the shelf
 * @param catalogue the types the fleet is made of
 * @param today the day every request is answered as of, when it is pinned
 * @param keys the keys the service accepts, when not the development ring
 */
public record Deps(
        Database database, Book book, TypeCatalogue catalogue, Optional<LocalDate> today, Optional<KeyRing> keys) {

    public Deps {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(catalogue, "catalogue");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(keys, "keys");
    }

    /** A service answering as of the real day, on the development ring. */
    public Deps(Database database, Book book, TypeCatalogue catalogue) {
        this(database, book, catalogue, Optional.empty(), Optional.empty());
    }

    /** A service answering as of a pinned day, on the development ring. */
    public Deps(Database database, Book book, TypeCatalogue catalogue, LocalDate today) {
        this(database, book, catalogue, Optional.of(today), Optional.empty());
    }

    /** The same from values that may be absent: no day answers as of today, no ring is the development one. */
    public static Deps of(
            Database database, Book book, TypeCatalogue catalogue, LocalDate todayOrNull, KeyRing keysOrNull) {
        return new Deps(database, book, catalogue, Optional.ofNullable(todayOrNull), Optional.ofNullable(keysOrNull));
    }

    /** The day a request is answered as of, which tests pin and production does not. */
    public LocalDate on() {
        return today.orElseGet(LocalDate::now);
    }

    /**
     * Run one request's work in its own unit of work and answer what it answers.
     *
     * <p>The unit commits when the work returns and rolls back when it throws, before anything is
     * written back to the caller, so a refused request leaves nothing behind and a commit that fails
     * never follows a success.
     */
    public <T> T apply(Function<UnitOfWork, T> work) {
        return UnitOfWork.apply(database, work);
    }

    /** Run one request's work that answers nothing in its own unit of work. */
    public void run(Consumer<UnitOfWork> work) {
        UnitOfWork.run(database, work);
    }
}
