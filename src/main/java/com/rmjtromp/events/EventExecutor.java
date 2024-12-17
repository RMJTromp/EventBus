package com.rmjtromp.events;

import org.jetbrains.annotations.NotNull;

/**
 * Interface which defines the class for event call backs to plugins
 */
public interface EventExecutor {
    void execute(@NotNull Object listener, @NotNull Event event) throws EventException;
}
