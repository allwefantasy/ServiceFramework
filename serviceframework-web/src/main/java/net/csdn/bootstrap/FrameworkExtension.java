package net.csdn.bootstrap;

import net.csdn.common.settings.Settings;

import java.util.Collections;
import java.util.List;

/**
 * One installable part of a single {@link ApplicationContext}.
 * <p>
 * {@code id} is unique. {@code provides} defaults to that id and is the name
 * other extensions put in {@code requires}. The context rejects duplicate ids,
 * duplicate capabilities, unknown requirements and cycles before {@link #register}
 * or {@link #start}. Registration order is the tie-break.
 * <p>
 * {@link #validate} must not open sockets, clients or class definitions.
 * {@link #register} may enhance and connect. {@link #start} runs only after
 * dependency injection exists and before HTTP or RPC ports open.
 * {@link #close} is idempotent and safe when {@link #start} never ran.
 * A disabled config entry is not loaded, so this class is not initialized.
 */
public interface FrameworkExtension {

    String id();

    default int version() {
        return 1;
    }

    default List<String> provides() {
        return Collections.singletonList(id());
    }

    default List<String> requires() {
        return Collections.emptyList();
    }

    /**
     * Called only after the implementation class was loaded. Config-disabled
     * names never reach this method.
     */
    default boolean enabled(Settings settings, ApplicationContext context) {
        return true;
    }

    default void validate(Settings settings, ApplicationContext context) {
    }

    default void register(Settings settings, ApplicationContext context) {
    }

    default void start(Settings settings, ApplicationContext context) {
    }

    /**
     * Release resources this extension opened. Must not read a closed
     * {@link net.csdn.common.enhancer.EnhancementContext}.
     */
    default void close() {
    }
}
