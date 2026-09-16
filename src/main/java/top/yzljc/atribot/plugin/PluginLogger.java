package top.yzljc.atribot.plugin;

import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginLogger
 * @Created_at 2026/09/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 * @Description 将插件 System.Logger 接入宿主日志，并自动标注插件名
 */
final class PluginLogger implements System.Logger {
    private static final org.slf4j.Logger DELEGATE = LoggerFactory.getLogger("top.yzljc.atribot.plugin.Plugin");
    private final String name;
    private final String prefix;

    PluginLogger(String pluginName) {
        name = "AtriMeow.Plugin." + Objects.requireNonNull(pluginName, "pluginName");
        prefix = "[" + pluginName + "] ";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(Level level) {
        return switch (Objects.requireNonNull(level, "level")) {
            case ALL, TRACE -> DELEGATE.isTraceEnabled();
            case DEBUG -> DELEGATE.isDebugEnabled();
            case INFO -> DELEGATE.isInfoEnabled();
            case WARNING -> DELEGATE.isWarnEnabled();
            case ERROR -> DELEGATE.isErrorEnabled();
            case OFF -> false;
        };
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
        if (isLoggable(level)) write(level, localize(bundle, message), thrown);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        if (!isLoggable(level)) return;
        String message = localize(bundle, format);
        if (message != null && params != null && params.length != 0) {
            message = MessageFormat.format(message, params);
        }
        write(level, message, null);
    }

    private void write(Level level, String message, Throwable thrown) {
        String content = prefix + message;
        switch (level) {
            case ALL, TRACE -> DELEGATE.trace(content, thrown);
            case DEBUG -> DELEGATE.debug(content, thrown);
            case INFO -> DELEGATE.info(content, thrown);
            case WARNING -> DELEGATE.warn(content, thrown);
            case ERROR -> DELEGATE.error(content, thrown);
            case OFF -> { }
        }
    }

    private static String localize(ResourceBundle bundle, String message) {
        if (bundle != null && message != null) {
            try {
                return bundle.getString(message);
            } catch (MissingResourceException ignored) {
            }
        }
        return message;
    }
}
