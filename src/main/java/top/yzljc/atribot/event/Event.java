package top.yzljc.atribot.event;

public abstract class Event {
    public String getEventName() {
        return getClass().getSimpleName();
    }
}
