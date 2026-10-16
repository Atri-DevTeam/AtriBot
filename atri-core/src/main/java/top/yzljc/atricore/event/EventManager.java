package top.yzljc.atricore.event;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程共享的事件管理器，在调用线程按优先级同步分发事件
 *
 * @Author YZ_Ljc_
 * @ClassName EventManager
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event
 */
@Slf4j
public class EventManager {

    private static final EventManager INSTANCE = new EventManager();

    public static EventManager getInstance() {
        return INSTANCE;
    }

    private final Map<Class<? extends Event>, List<RegisteredListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 注册监听器当前类声明的事件处理方法，不扫描继承方法
     *
     * @param listener 监听器实例；重复注册会重复添加处理方法
     */
    public void registerEvents(Listener listener) {
        Method[] methods = listener.getClass().getDeclaredMethods();
        for (Method method : methods) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 1) {
                continue;
            }

            Class<?> eventClass = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventClass)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> clazz = (Class<? extends Event>) eventClass;

            RegisteredListener regListener = new RegisteredListener(listener, method, annotation.priority(), annotation.ignoreCancelled());

            listeners.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>()).add(regListener);

            listeners.get(clazz).sort(Comparator.comparingInt(a -> a.priority().getSlot()));
        }
    }

    /**
     * 移除指定监听器实例的全部处理方法
     *
     * @param listener 注册时使用的同一实例
     */
    public void unregisterEvents(Listener listener) {
        for (List<RegisteredListener> list : listeners.values()) {
            list.removeIf(reg -> reg.listener() == listener);
        }
    }

    /**
     * 在当前线程按优先级分发事件，仅匹配具体运行时类型
     * <p>处理器异常记入日志后继续分发，取消标记是否阻止业务由事件生产者判断
     *
     * @param event 待分发的事件
     */
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
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("调用事件处理器失败: {}", cause.getMessage(), cause);
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
