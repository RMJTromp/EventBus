package com.rmjtromp.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class EventsManager {

    @Getter
    private static EventsManager instance = null;
    private static final List<Object> queue = new ArrayList<>();

    public static void init() {
        if(instance == null) instance = new EventsManager();
        for(Object listener : queue) registerEvents(listener);
        if(!queue.isEmpty()) queue.clear();
    }

    public static void registerEvents(@NotNull Object listener) {
        if(instance != null) {
            for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry : RegisteredListener.createRegisteredListeners(listener).entrySet()) {
                Class<? extends Event> clazz = entry.getKey();
                Set<RegisteredListener> value = entry.getValue();
                HandlerList list = getEventListeners(clazz);
                if(list != null) list.registerAll(value);
            }
        } else queue.add(listener);
    }

    public static void callEvent(@NotNull Event event) {
        HandlerList handlers = event.getHandlers();
        RegisteredListener[] listeners = handlers.getRegisteredListeners();

        event.called = true;
        for (RegisteredListener registration : listeners) {
            try {
                registration.callEvent(event);
            } catch (Exception ex) {
                log.warning("Could not pass event " + event.getEventName() + " to " + registration.getListener().getClass().getName());
                log.throwing(EventsManager.class.getName(), "callEvent", ex);
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
