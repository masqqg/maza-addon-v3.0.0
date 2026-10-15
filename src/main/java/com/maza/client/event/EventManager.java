package com.maza.client.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    public static final EventManager INSTANCE = new EventManager();

    private record Listener(Object target, Method method, int priority) {}

    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void subscribe(Object object) {
        if (object == null) return;
        Class<?> clazz = object.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)) {
                if (method.getParameterCount() == 1) {
                    Class<?> eventType = method.getParameterTypes()[0];
                    method.setAccessible(true);
                    int priority = method.getAnnotation(EventHandler.class).priority();
                    Listener listener = new Listener(object, method, priority);
                    listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
                    listeners.get(eventType).sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                }
            }
        }
    }

    public void unsubscribe(Object object) {
        if (object == null) return;
        for (List<Listener> list : listeners.values()) {
            list.removeIf(l -> l.target() == object);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        if (event == null) return null;
        List<Listener> list = listeners.get(event.getClass());
        if (list != null && !list.isEmpty()) {
            for (Listener listener : list) {
                try {
                    listener.method().invoke(listener.target(), event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return event;
    }
}
