package top.yzljc.atribot.command;

import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
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

    boolean hasPermission();

    boolean hasPermission(String permission);

    String sendMessage(String text);
}
