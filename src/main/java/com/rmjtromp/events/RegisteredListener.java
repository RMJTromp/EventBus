package com.rmjtromp.events;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stores relevant information for plugin listeners
 */
public class RegisteredListener {

    @Getter
    private final @NotNull Object listener;

    @Getter
    private final @NotNull EventPriority priority;
    private final @NotNull EventExecutor executor;
    private final boolean ignoreCancelled;

    public RegisteredListener(final @NotNull Object listener, final @NotNull EventExecutor executor, final @NotNull EventPriority priority, final boolean ignoreCancelled) {
        this.listener = listener;
        this.priority = priority;
        this.executor = executor;
        this.ignoreCancelled = ignoreCancelled;
    }

    /**
     * Calls the event executor
     *
     * @param event The event
     * @throws EventException If an event handler throws an exception.
     */
    public void callEvent(final @NotNull Event event) throws EventException {
        if (event instanceof Cancellable){
            if (((Cancellable) event).isCancelled() && isIgnoringCancelled()){
                return;
            }
        }
        executor.execute(listener, event);
    }

    /**
     * Whether this listener accepts cancelled events
     *
     * @return True when ignoring cancelled events
     */
    public boolean isIgnoringCancelled() {
        return ignoreCancelled;
    }

    @NotNull
    private static Set<Method> getMethodsRecursively(@NotNull Class<?> clazz) {
        Set<Method> methods = new HashSet<>();
        while (clazz != null) {
            for (Method method : clazz.getDeclaredMethods())
                if(method.isAnnotationPresent(EventHandler.class)) methods.add(method);

            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    @NotNull
    public static Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(@NotNull Object listener) {
        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();
        Set<Method> methods = getMethodsRecursively(listener.getClass());

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                System.out.print("Attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.computeIfAbsent(eventClass, k -> new HashSet<>());

            EventExecutor executor = (listener1, event) -> {
                try {
                    if (!eventClass.isAssignableFrom(event.getClass())) {
                        return;
                    }
                    method.invoke(listener1, event);
                } catch (InvocationTargetException ex) {
                    throw new EventException(ex.getCause());
                } catch (Throwable t) {
                    throw new EventException(t);
                }
            };
            eventSet.add(new RegisteredListener(listener, executor, eh.priority(), eh.ignoreCancelled()));
        }
        return ret;
    }
}