package top.yzljc.atricore.platform;

/**
 * 可撤回消息的操作契约，撤回权限与结果由平台决定
 *
 * @Author YZ_Ljc_
 * @ClassName Recallable
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.platform
 */
public interface Recallable {
    /**
     * 撤回当前消息并等待平台操作结果
     *
     * @return 平台确认撤回成功时返回 true，失败时返回 false
     */
    boolean recall();
}