package top.yzljc.atricore.event;

/**
 * 事件取消状态契约，取消后的业务行为由事件生产者决定
 *
 * @Author YZ_Ljc_
 * @ClassName Cancellable
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancel);
}
