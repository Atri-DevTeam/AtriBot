package top.yzljc.atribot.service.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class RunScheduleTask {
    private static final Logger log = LoggerFactory.getLogger(RunScheduleTask.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private static final String SCHEDULE_PACKAGE = "top.yzljc.atribot.function.task";

    public static void runAllTasks() {
        List<Class<?>> taskClasses = findClassesInPackage(SCHEDULE_PACKAGE);
        for (Class<?> clazz : taskClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                Schedule[] anns = method.getAnnotationsByType(Schedule.class);
                if (anns == null || anns.length == 0) continue;
                if (!method.trySetAccessible()) {
                    log.warn("无法访问定时方法 {}.{}", clazz.getSimpleName(), method.getName());
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    log.warn("定时任务方法必须无参: {}.{}", clazz.getSimpleName(), method.getName());
                    continue;
                }
                for (Schedule ann : anns) {
                    register(method, ann);
                }
            }
        }
        log.info("所有定时任务已启动");
    }

    public static void shutdown() {
        scheduler.shutdownNow();
    }

    private static List<Class<?>> findClassesInPackage(String packageName) {
        String path = packageName.replace('.', '/');
        ClassLoader cl = RunScheduleTask.class.getClassLoader();
        List<Class<?>> out = new ArrayList<>();
        Set<String> addedClassNames = new HashSet<>();
        try {
            var resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URI uri = resources.nextElement().toURI();
                if (uri.getScheme().equals("file")) {
                    try (Stream<Path> walk = Files.walk(Path.of(uri), 1)) {
                        walk.filter(p -> p.getFileName().toString().endsWith(".class"))
                                .forEach(p -> addClass(out, addedClassNames, packageName, p.getFileName().toString(), cl));
                    }
                } else if (uri.getScheme().equals("jar")) {
                    try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
                        Path root = fs.getPath("/");
                        try (Stream<Path> walk = Files.walk(root)) {
                            walk.filter(p -> {
                                        String s = p.toString().replace('\\', '/');
                                        if (!s.endsWith(".class")) return false;
                                        String prefix = path + "/", prefixSlash = "/" + path + "/";
                                        if (!s.startsWith(prefix) && !s.startsWith(prefixSlash)) return false;
                                        int after = s.startsWith(prefixSlash) ? prefixSlash.length() : prefix.length();
                                        return s.substring(after).indexOf('/') < 0;
                                    })
                                    .forEach(p -> {
                                        String rel = p.toString().replace('/', '.').replace('\\', '.');
                                        if (rel.startsWith(".")) rel = rel.substring(1);
                                        String className = rel.substring(0, rel.length() - 6);
                                        addClassByName(out, addedClassNames, className, cl);
                                    });
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("扫描 schedule 包失败，回退为空列表", e);
        }
        return out;
    }

    private static void addClass(List<Class<?>> out, Set<String> addedClassNames, String packageName, String fileName, ClassLoader cl) {
        String className = packageName + '.' + fileName.substring(0, fileName.length() - 6);
        addClassByName(out, addedClassNames, className, cl);
    }

    private static void addClassByName(List<Class<?>> out, Set<String> addedClassNames, String className, ClassLoader cl) {
        if (!addedClassNames.add(className)) return;
        try {
            out.add(Class.forName(className, false, cl));
        } catch (ClassNotFoundException e) {
            log.debug("无法加载类: {}", className);
        }
    }

    private static void register(Method method, Schedule ann) {
        Runnable task = () -> {
            try {
                Object instance = null;

                // 非 static 方法
                if (!Modifier.isStatic(method.getModifiers())) {
                    Class<?> clazz = method.getDeclaringClass();

                    try {
                        // Kotlin object
                        instance = clazz.getField("INSTANCE").get(null);
                    } catch (NoSuchFieldException ignored) {
                        // 普通 Java 类
                        try {
                            instance = clazz.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            log.error("无法创建定时任务实例: {}.{}", clazz.getSimpleName(), method.getName(), e);
                            return;
                        }
                    }
                }

                method.invoke(instance);
            } catch (Exception e) {
                log.error("定时任务执行异常: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName(), e);
            }
        };
        if (ann.type() == ScheduleType.DAILY) {
            int[] hms = parseDailyTime(ann.time());
            if (hms != null) {
                scheduleDailyTask(task, hms[0], hms[1], hms[2]);
            } else {
                log.warn("无效的 DAILY time: {} @ {}.{}", ann.time(), method.getDeclaringClass().getSimpleName(), method.getName());
            }
        } else if (ann.type() == ScheduleType.HALF_HOURLY) {
            int[] ms = parseHalfHourlyTime(ann.time());
            if (ms != null) {
                scheduleHalfHourlyTask(task, ms[0], ms[1]);
            } else {
                log.warn("无效的 HALF_HOURLY time: {} @ {}.{}", ann.time(), method.getDeclaringClass().getSimpleName(), method.getName());
            }
        } else {
            int[] ms = parseHourlyTime(ann.time());
            if (ms != null) {
                scheduleHourlyTask(task, ms[0], ms[1]);
            } else {
                log.warn("无效的 HOURLY time: {} @ {}.{}", ann.time(), method.getDeclaringClass().getSimpleName(), method.getName());
            }
        }
    }

    /**
     * DAILY: "HH:mm:ss" -> [hour, min, sec]
     */
    private static int[] parseDailyTime(String time) {
        String[] parts = time.trim().split(":");
        if (parts.length < 3) return null;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());
            int sec = Integer.parseInt(parts[2].trim());
            if (hour < 0 || hour > 23 || min < 0 || min > 59 || sec < 0 || sec > 59) return null;
            return new int[]{hour, min, sec};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * HOURLY: "mm:ss" -> [minute, second]
     */
    private static int[] parseHourlyTime(String time) {
        String[] parts = time.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int min = Integer.parseInt(parts[0].trim());
            int sec = Integer.parseInt(parts[1].trim());
            if (min < 0 || min > 59 || sec < 0 || sec > 59) return null;
            return new int[]{min, sec};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * HALF_HOURLY: "mm:ss" (mm 范围 0-29) -> [minute, second]，每半小时触发一次，分别在每小时的 mm:ss 和 (mm+30):ss
     */
    private static int[] parseHalfHourlyTime(String time) {
        String[] parts = time.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int min = Integer.parseInt(parts[0].trim());
            int sec = Integer.parseInt(parts[1].trim());
            if (min < 0 || min > 29 || sec < 0 || sec > 59) return null;
            return new int[]{min, sec};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void scheduleDailyTask(Runnable task, int hour, int min, int sec) {
        long delayMillis = computeInitialDelayByDayMillis(hour, min, sec);
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("定时任务执行异常", e);
            } finally {
                scheduleDailyTask(task, hour, min, sec);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void scheduleHourlyTask(Runnable task, int minute, int second) {
        long delayMillis = computeInitialDelayByHourMillis(minute, second);
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("整点任务执行异常", e);
            } finally {
                scheduleHourlyTask(task, minute, second);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void scheduleHalfHourlyTask(Runnable task, int minute, int second) {
        long delayMillis = computeInitialDelayByHalfHourMillis(minute, second);
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("半小时任务执行异常", e);
            } finally {
                scheduleHalfHourlyTask(task, minute, second);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static long computeInitialDelayByDayMillis(int hour, int min, int sec) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(hour, min, sec);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static long computeInitialDelayByHourMillis(int minute, int second) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withMinute(minute).withSecond(second).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusHours(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static long computeInitialDelayByHalfHourMillis(int minute, int second) {
        LocalDateTime now = LocalDateTime.now();
        // 两个候选：当前小时的 mm:ss 和 (mm+30):ss
        LocalDateTime candidate1 = now.withMinute(minute).withSecond(second).withNano(0);
        LocalDateTime candidate2 = now.withMinute(minute + 30).withSecond(second).withNano(0);

        LocalDateTime next;
        if (now.isBefore(candidate1)) {
            next = candidate1;
        } else if (now.isBefore(candidate2)) {
            next = candidate2;
        } else {
            // 两个候选都已过，取下一小时的第一个候选
            next = candidate1.plusHours(1);
        }
        return Duration.between(now, next).toMillis();
    }
}
