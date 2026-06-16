package top.yzljc.atribot.event;

public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancel);
}
