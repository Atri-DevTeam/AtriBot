package top.yzljc.qqbot.utils;

import org.slf4j.LoggerFactory;

public class Logger {

    private static org.slf4j.Logger getLogger() {
        String className = Thread.currentThread().getStackTrace()[3].getClassName();
        return LoggerFactory.getLogger(className);
    }

    public static void info(String format, Object... arguments) {
        getLogger().info(format, arguments);
    }

    public static void info(String msg) {
        getLogger().info(msg);
    }

    public static void warn(String format, Object... arguments) {
        getLogger().warn(format, arguments);
    }

    public static void warn(String msg) {
        getLogger().warn(msg);
    }

    public static void warn(String msg, Throwable t) {
        getLogger().warn(msg, t);
    }

    public static void error(String format, Object... arguments) {
        getLogger().error(format, arguments);
    }

    public static void error(String msg) {
        getLogger().error(msg);
    }

    public static void error(String msg, Throwable t) {
        getLogger().error(msg, t);
    }

    public static void debug(String format, Object... arguments) {
        getLogger().debug(format, arguments);
    }

    public static void debug(String msg) {
        getLogger().debug(msg);
    }
}

