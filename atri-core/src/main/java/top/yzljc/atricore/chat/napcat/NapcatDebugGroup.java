package top.yzljc.atricore.chat.napcat;

import top.yzljc.atricore.AtriProvider;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.concurrent.RejectedExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatDebugGroup
 * @Created_at 2026/09/25
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.napcat
 */
@Slf4j
public final class NapcatDebugGroup {

    public static void sendAsync(String message) {
        String groupId = AtriProvider.getInstance().getSettings().getNapcatDebugGroupUin();
        if (groupId == null || groupId.isBlank()) return;
        try {
            ThreadManager.execute(() -> GroupMessage.chatMessage(groupId, message));
        } catch (RejectedExecutionException e) {
            log.warn("Napcat 调试群发送任务被拒绝，已跳过", e);
        }
    }
}
