package top.yzljc.atricore.command;

import top.yzljc.atricore.platform.Platform;

/**
 * 指令发送者的公共能力，平台特有操作通过对应发送者接口提供
 *
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public interface CommandSender {

    /**
     * @return 发送者所属平台；控制台等没有平台的发送者返回 null
     */
    default Platform getPlatform() {
        return null;
    }

    String getUserId();

    String getUsername();

    /**
     * 检查机器人管理权限
     *
     * @return 具备机器人管理权限时返回 true，平台群内角色另行判断
     */
    boolean hasPermission();

    /**
     * 检查指定权限节点
     *
     * @param permission 权限节点
     * @return 具备管理权限或被授予该权限时返回 true，具体判断由实现提供
     */
    boolean hasPermission(String permission);

    /**
     * 向指令来源发送纯文本回复
     *
     * @param text 回复内容
     * @return 平台消息 ID；发送失败或发送者不产生平台消息时可为 null
     */
    String sendMessage(String text);
}
