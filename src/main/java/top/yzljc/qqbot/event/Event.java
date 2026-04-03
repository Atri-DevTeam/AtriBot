package top.yzljc.qqbot.event;

public abstract class Event {
    public String getEventName() {
        return getClass().getSimpleName();
    }
}
