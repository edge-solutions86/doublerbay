package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One command that does something, as opposed to a group that only holds commands.
 *
 * <p>Every command opens its own unit of work and closes it. A long running session holding a
 * transaction open across a coffee break is how a maintenance system ends up with a locked row
 * nobody can explain. So the work a command does is handed to {@link #work} and committed when it
 * returns, and anything it prints afterwards is printed from values already read.
 */
abstract class Step implements Callable<Integer> {

    private Main main;

    /** Do the command, answering the exit code. */
    @Override
    public abstract Integer call();

    /** Run this command from the command line that reached it, answering the exit code. */
    final Integer callFrom(Main reached) {
        this.main = reached;
        return call();
    }

    /** The command line this command was reached from. */
    final Main main() {
        return main;
    }

    /** Print one line, or several separated by line breaks, to standard output. */
    final void say(String text) {
        main().say(text);
    }

    /** Run some work in one unit of work and hand back what it answered. */
    final <T> T work(Function<UnitOfWork, T> work) {
        return UnitOfWork.apply(main().database(), work);
    }

    /** Run some work that answers nothing in one unit of work. */
    final void inWork(Consumer<UnitOfWork> work) {
        UnitOfWork.run(main().database(), work);
    }

    /** Whether an optional value was given at all: an empty one counts as left off. */
    static boolean given(String value) {
        return value != null && !value.isEmpty();
    }
}
