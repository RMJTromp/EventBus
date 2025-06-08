package com.rmjtromp.events;

import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Log
@UtilityClass
public final class EventBus {

    private static final List<Consumer<Class<? extends Event>>> firstListenerCallbacks = new ArrayList<>();
    static final List<Consumer<Class<? extends Event>>> lastListenerCallbacks = new ArrayList<>();

    /**
     * Register a callback that will be triggered when an event type gets its first listener
     * @param callback Consumer that will be called with the event class when it gets its first listener
     */
    @NotNull
    @Contract(pure = true)
    public static Runnable onFirstListenerRegistered(@NotNull Consumer<Class<? extends Event>> callback) {
        synchronized (firstListenerCallbacks) {
            firstListenerCallbacks.add(callback);
        }

        return () -> {
            synchronized (firstListenerCallbacks) {
                firstListenerCallbacks.remove(callback);
            }
        };
    }

    /**
     * Register a callback that will be triggered when an event type loses its last listener
     * @param callback Consumer that will be called with the event class when it loses its last listener
     */
    @NotNull
    @Contract(pure = true)
    public static Runnable onLastListenerUnregistered(@NotNull Consumer<Class<? extends Event>> callback) {
        synchronized(lastListenerCallbacks) {
            lastListenerCallbacks.add(callback);
        }

        return () -> {
            synchronized (lastListenerCallbacks) {
                lastListenerCallbacks.remove(callback);
            }
        };
    }

    public static void register(@NotNull Object listener) {
        for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry : RegisteredListener.createRegisteredListeners(listener).entrySet()) {
            Class<? extends Event> clazz = entry.getKey();
            Set<RegisteredListener> value = entry.getValue();
            HandlerList list = getEventListeners(clazz);
            if(list != null) {
                boolean wasEmpty = list.getRegisteredListeners().length == 0;
                list.registerAll(value);
                if(wasEmpty && list.getRegisteredListeners().length > 0) {
                    synchronized(firstListenerCallbacks) {
                        for(Consumer<Class<? extends Event>> callback : firstListenerCallbacks) {
                            callback.accept(clazz);
                        }
                    }
                }
            }
        }
    }

    public static void post(@NotNull Event event) {
        HandlerList handlers = event.getHandlers();
        RegisteredListener[] listeners = handlers.getRegisteredListeners();

        event.called = true;
        for (RegisteredListener registration : listeners) {
            try {
                registration.callEvent(event);
            } catch (Exception ex) {
                log.warning("Could not pass event " + event.getEventName() + " to " + registration.getListener().getClass().getName());
                log.throwing(EventBus.class.getName(), "callEvent", ex);
                ex.printStackTrace();
            }
        }
    }

    private static HandlerList getEventListeners(@NotNull Class<? extends Event> type) {
        try {
            return Event.getHandlersMap().computeIfAbsent(type, k -> new HandlerList());
        } catch (Exception e) {/* ignore */}
        return null;
    }

}
