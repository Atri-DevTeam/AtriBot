package top.yzljc.atribot.service.runtime;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;

public class JLineConsoleAppender extends AppenderBase<ILoggingEvent> {

    @Setter
    private String pattern;
    private PatternLayout layout;

    @Override
    public void start() {
        if (pattern == null || pattern.isBlank()) {
            addError("No pattern set for JLineConsoleAppender");
            return;
        }
        layout = new PatternLayout();
        layout.setContext(getContext());
        layout.setPattern(pattern);
        layout.start();
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        ConsoleManager.printConsole(layout.doLayout(event));
    }
}
