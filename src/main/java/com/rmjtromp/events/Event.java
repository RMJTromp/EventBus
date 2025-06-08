package com.rmjtromp.events;

import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Contract;

/**
 * Represents an event.
 */
@NoArgsConstructor
public abstract class Event {

    private String name;
    boolean async = false;

    boolean called = false;

    /**
     * Convenience method for providing a user-friendly identifier. By
     * default, it is the event's class's {@linkplain Class#getSimpleName()
     * simple name}.
     *
     * @return name of this event
     */
    @Contract(pure = true)
    public String getEventName() {
        if (name == null) {
            name = getClass().getSimpleName();
        }
        return name;
    }

    /**
     * Any custom event that should not by synchronized with other events must
     * use the specific constructor. These are the caveats of using an
     * asynchronous event:
     * <ul>
     * <li>The event is never fired from inside code triggered by a
     *     synchronous event. Attempting to do so results in an {@link
     *     IllegalStateException}.
     * <li>However, asynchronous event handlers may fire synchronous or
     *     asynchronous events
     * <li>The event may be fired multiple times simultaneously and in any
     *     order.
     * <li>Any newly registered or unregistered handler is ignored after an
     *     event starts execution.
     * <li>The handlers for this event may block for any length of time.
     * <li>Some implementations may selectively declare a specific event use
     *     as asynchronous. This behavior should be clearly defined.
     * <li>Asynchronous calls are not calculated in the plugin timing system.
     * </ul>
     *
     * @return false by default, true if the event fires asynchronously
     */
    @Contract(pure = true)
    public final boolean isAsynchronous() {
        return async;
    }

    /**
     * Indicates whether the event has been marked as "called." This typically
     * signifies that the event has been dispatched to its registered handlers
     * using the event system.
     *
     * @return true if the event has been marked as called; false otherwise
     */
    @Contract(pure = true)
    public boolean hasBeenCalled() {
        return called;
    }

}