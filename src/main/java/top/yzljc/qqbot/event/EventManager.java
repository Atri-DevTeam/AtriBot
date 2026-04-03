package top.yzljc.qqbot.event;

import top.yzljc.qqbot.utils.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {

    private static final EventManager INSTANCE = new EventManager();

    public static EventManager getInstance() {
        return INSTANCE;
    }

    private final Map<Class<? extends Event>, List<RegisteredListener>> listeners = new ConcurrentHashMap<>();

    public void registerEvents(Listener listener) {
        Method[] methods = listener.getClass().getDeclaredMethods();
        for (Method method : methods) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 1) {
                continue; // Invalid method signature
            }

            Class<?> eventClass = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventClass)) {
                continue; // Not an event
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> clazz = (Class<? extends Event>) eventClass;

            RegisteredListener regListener = new RegisteredListener(listener, method, annotation.priority(), annotation.ignoreCancelled());

            listeners.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>()).add(regListener);

            // Sort by priority (Lowest to Monitor)
            listeners.get(clazz).sort(Comparator.comparingInt(a -> a.priority().getSlot()));
        }
    }

    public void unregisterEvents(Listener listener) {
        for (List<RegisteredListener> list : listeners.values()) {
            list.removeIf(reg -> reg.listener() == listener);
        }
    }

    public void callEvent(Event event) {
        List<RegisteredListener> eventListeners = listeners.get(event.getClass());
        if (eventListeners == null) return;

        for (RegisteredListener regListener : eventListeners) {
            if (event instanceof Cancellable) {
                if (((Cancellable) event).isCancelled() && regListener.ignoreCancelled()) {
                    continue;
                }
            }
            try {
                regListener.method().invoke(regListener.listener(), event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Logger.error("调用事件处理器失败: " + e.getMessage());
            }
        }
    }

    private record RegisteredListener(Listener listener, Method method, EventPriority priority,
                                      boolean ignoreCancelled) {
            private RegisteredListener(Listener listener, Method method, EventPriority priority, boolean ignoreCancelled) {
                this.listener = listener;
                this.method = method;
                this.priority = priority;
                this.ignoreCancelled = ignoreCancelled;
                this.method.setAccessible(true);
            }

        }
}
