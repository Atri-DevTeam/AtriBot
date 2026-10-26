package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.event.events.EmailMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atricore.chat.napcat.GroupInformation;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName CodexResetAlert
 * @Created_at 2026/10/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
public final class CodexResetAlert implements Listener {

    private static final String codex_reset_email = "alerts@notify.codex-reset.com";
    private static final String forward_email = "3199590352@qq.com";
    private static final Pattern ALERT_HEADER = Pattern.compile(
            "STRICT\\h+#ALERTS\\h+FEED\\h*/\\h*(?:FORECAST|RESET)\\b");
    private static final String ALERT_END = "Open Codex Reset";

    @EventHandler
    public void onEmailReceived(EmailMessageEvent event) {
        if (event.getAuthors().stream().noneMatch(a -> a.contains(forward_email))) {
            return;
        }
        String plainText = event.getPlainText();
        if (plainText == null || !plainText.contains(codex_reset_email)) {
            return;
        }

        String alertContent = extractAlertContent(plainText);
        if (alertContent.isEmpty()) {
            return;
        }

        var gps = GroupInformation.fetchAllGroupIds().stream().filter(a -> GroupConfigManager.isFeatureEnabled(a, "codex_reset_alert")).collect(Collectors.toSet());
        for (var a : gps) {
            GroupMessage.chatMessage(a, "Codex 重置动态\n---------------------\n" + alertContent.trim());
        }
    }

    /**
     * 提取正文通知区块，不读取邮件主题；缺少任一边界时返回空字符串。
     */
    public static String extractAlertContent(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        Matcher header = ALERT_HEADER.matcher(plainText);
        if (!header.find()) {
            return "";
        }
        int end = plainText.indexOf(ALERT_END, header.end());
        if (end < 0) {
            return "";
        }
        // 转发邮件的表格布局可能产生单独一行的竖线。
        return plainText.substring(header.end(), end)
                .replaceAll("(?m)^\\h*\\|\\h*$", "")
                .strip();
    }
}
