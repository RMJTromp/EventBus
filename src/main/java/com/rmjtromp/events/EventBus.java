package com.rmjtromp.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Log
public final class EventBus implements Closeable {

    private final List<Consumer<Class<? extends Event>>> firstListenerCallbacks = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<Class<? extends Event>>> lastListenerCallbacks = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<Class<? extends Event>, HandlerList> handlersMap = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            Thread t = new Thread(r, "EventBus-Async");
            t.setDaemon(true);
            return t;
        }
    );


    /**
     * Register a callback that will be triggered when an event type gets its first listener
     * @param callback Consumer that will be called with the event class when it gets its first listener
     */
    @NotNull
    @Contract(pure = true)
    public Runnable onFirstListenerRegistered(@NotNull Consumer<Class<? extends Event>> callback) {
        firstListenerCallbacks.add(callback);
        return () -> firstListenerCallbacks.remove(callback);
    }

    /**
     * Register a callback that will be triggered when an event type loses its last listener
     * @param callback Consumer that will be called with the event class when it loses its last listener
     */
    @NotNull
    @Contract(pure = true)
    public Runnable onLastListenerUnregistered(@NotNull Consumer<Class<? extends Event>> callback) {
        lastListenerCallbacks.add(callback);
        return () -> lastListenerCallbacks.remove(callback);
    }

    /**
     * Registers a listener to the event handling system. This allows the listener
     * to receive and handle events it is subscribed to.
     *
     * @param listener The object containing methods annotated for handling events.
     */
    public void register(@NotNull Object listener) {
        for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry : RegisteredListener.createRegisteredListeners(listener).entrySet()) {
            Class<? extends Event> clazz = entry.getKey();
            Set<RegisteredListener> value = entry.getValue();
            HandlerList list = handlersMap.computeIfAbsent(clazz, k -> new HandlerList());
            boolean wasEmpty = list.getRegisteredListeners().length == 0;
            list.registerAll(value);
            if(wasEmpty && list.getRegisteredListeners().length > 0) {
                List<Consumer<Class<? extends Event>>> callbacks = new ArrayList<>(firstListenerCallbacks);
                for(Consumer<Class<? extends Event>> callback : callbacks) {
                    callback.accept(clazz);
                }
            }
        }
    }

    /**
     * Unregisters a listener from the event handling system. This removes the listener
     * from all events it was registered to, and if it was the last listener for an event,
     * it will trigger the last listener unregistered callbacks.
     *
     * @param listener The object that was previously registered to handle events.
     */
    public void unregister(@NotNull Object listener) {
        for (Map.Entry<Class<? extends Event>, HandlerList> entry : handlersMap.entrySet()) {
            HandlerList handlerList = entry.getValue();
            boolean wasNotEmpty = handlerList.getRegisteredListeners().length > 0;

            if (handlerList.unregister(listener)) {
                boolean isEmpty = handlerList.getRegisteredListeners().length == 0;
                //noinspection ConstantValue
                if (wasNotEmpty && isEmpty) {
                    Class<? extends Event> eventClass = entry.getKey();
                    // Create a copy of the list to avoid potential ConcurrentModificationException
                    List<Consumer<Class<? extends Event>>> callbacks = new ArrayList<>(lastListenerCallbacks);
                    for (Consumer<Class<? extends Event>> callback : callbacks) {
                        callback.accept(eventClass);
                    }
                }
            }
        }
    }

    /**
     * Posts an event to all registered listeners that handle the event type.
     * The event is marked as "called" and is passed to each listener in the order
     * of their registration. In case a listener throws an exception while handling
     * the event, it will be logged, and the exception will be printed to the stack trace.
     *
     * @param event The event to be posted to the registered listeners.
     */
    @NotNull
    public <T extends Event> T post(@NotNull T event) {
        HandlerList handlers = handlersMap.getOrDefault(event.getClass(), new HandlerList());
        RegisteredListener[] listeners = handlers.getRegisteredListeners();

        event.called = true;
        for (RegisteredListener registration : listeners) {
            try {
                registration.callEvent(event);
            } catch (Exception ex) {
                log.warning("Could not pass event " + event.getEventName() + " to " + registration.getListener().getClass().getName());
                log.throwing(EventBus.class.getName(), "callEvent", ex);
            }
        }
        return event;
    }

    /**
     * Posts an event asynchronously. The event will be posted in a separate thread,
     * and the method returns an EventPromise that can be used to handle the result
     * of the event posting.
     *
     * @param event The event to be posted asynchronously.
     * @param <T>   The type of the event.
     * @return An EventPromise that can be used to handle the result of the event posting.
     */
    @NotNull
    @Contract(pure = true)
    public <T extends Event> EventPromise<T> postAsync(@NotNull T event) {
        EventPromise<T> promise = new EventPromise<>(event);
        event.async = true;
        CompletableFuture
            .runAsync(() -> post(event), asyncExecutor)
            .thenAccept(v -> promise.markResolved());
        return promise;
    }

    /**
     * Retrieves a map containing all event types and their corresponding handler lists.
     * This map associates each event class with its respective {@link HandlerList}.
     *
     * @return A new {@link HashMap} containing event classes as keys and their associated
     * handler lists as values.
     */
    @NotNull
    @Contract(pure = true)
    public HashMap<Class<? extends Event>, HandlerList> getHandlerLists() {
        return new HashMap<>(handlersMap);
    }

    /**
     * Retrieves the handler list associated with a specific event class. If no handler list
     * is associated with the given event class, a new empty HandlerList is returned.
     *
     * @param eventClass the class of the event for which the handler list is being retrieved
     * @return the handler list associated with the specified event class, or a new empty HandlerList
     *         if none exists
     */
    @NotNull
    @Contract(pure = true)
    public HandlerList getHandlerList(@NotNull Class<? extends Event> eventClass) {
        return handlersMap.getOrDefault(eventClass, new HandlerList());
    }

    @Override
    public void close() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS))
                asyncExecutor.shutdownNow();
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @RequiredArgsConstructor
    public static class EventPromise<T extends Event> {
        private final T event;
        private volatile boolean resolved = false;
        private volatile Consumer<T> onResolveCallback = null;


        /**
         * Registers a callback that will be executed when the event associated with this
         * promise is resolved. If the event is already resolved at the time of registering,
         * the callback is immediately executed with the event.
         *
         * @param callback the callback function to be executed when the event is resolved
         */
        public synchronized void then(@NotNull Consumer<T> callback) {
            onResolveCallback = callback;
            if (resolved) callback.accept(event);
        }

        private synchronized void markResolved() {
            resolved = true;
            if (onResolveCallback != null)
                onResolveCallback.accept(event);
        }

    }

}
