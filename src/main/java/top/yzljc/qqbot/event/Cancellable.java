package top.yzljc.qqbot.event;

public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean cancel);
}
