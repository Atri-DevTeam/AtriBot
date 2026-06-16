package top.yzljc.atribot.utils.statistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.NapcatPrivateMessageEvent;
import top.yzljc.atribot.chat.napcat.GroupInformation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ScheduledFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName BotRuntimeData
 * @Created_at 2026/04/15
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
@Slf4j
public class BotRuntimeData implements Listener {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final File file = new File("data.json");
    private static RuntimeData cache;
    @Getter
    private static ScheduledFuture<?> task;

    public static void init() throws Exception {
        if (!file.exists()) {
            cache = RuntimeData.createDefault();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, cache);
        } else {
            cache = mapper.readValue(file, RuntimeData.class);
            if (cache.getGroup_message_data() == null) {
                cache.setGroup_message_data(new java.util.HashMap<>());
            }
        }
        task = Atri.getInstance().getScheduler().runTaskTimer(
                BotRuntimeData::save, 0L, 60L * 1000L
        );
    }

    public static RuntimeData get() {
        return cache;
    }

    public static synchronized void save() {
        try {
            File target = new File("data.json");
            File temp = new File("data.json.tmp");

            mapper.writerWithDefaultPrettyPrinter().writeValue(temp, cache);

            try {
                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (Exception e) {
                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception e) {
            log.error("保存运行时数据失败: " + e.getMessage());
        }
    }

    @EventHandler
    public void onGroupMessageReceived(NapcatGroupMessageEvent event) {
        String groupId = event.getGroupId();
        String groupName = GroupInformation.getGroupName(groupId);
        callGroupMessageReceive(groupId);
    }

    @EventHandler
    public void onPrivateMessageReceived(NapcatPrivateMessageEvent event) {
        callPrivateMessageReceive();
    }

    public static synchronized void callGroupMessageReceive(String groupId) {
        RuntimeData data = cache;
        data.setTotal_group_message_received(data.getTotal_group_message_received() + 1);
        RuntimeData.GroupMessageData group = data.getOrCreateGroup(groupId, GroupInformation.getGroupName(groupId));
        group.setMessage_received(group.getMessage_received() + 1);
    }

    public static synchronized void callGroupMessageSend(String groupId) {
        RuntimeData data = cache;
        data.setTotal_group_message_send(data.getTotal_group_message_send() + 1);
        RuntimeData.GroupMessageData group = data.getOrCreateGroup(groupId, GroupInformation.getGroupName(groupId));
        group.setMessage_send(group.getMessage_send() + 1);
    }

    public static synchronized void callPrivateMessageReceive() {
        RuntimeData data = cache;
        data.setTotal_private_message_received(data.getTotal_private_message_received() + 1);
    }

    public static synchronized void callPrivateMessageSend() {
        RuntimeData data = cache;
        data.setTotal_private_message_send(data.getTotal_private_message_send() + 1);
    }

    public static synchronized void callRecallMessage() {
        RuntimeData data = cache;
        data.setTotal_group_message_recalled(data.getTotal_group_message_recalled() + 1);
    }

    public static synchronized void callAntiBotAction() {
        RuntimeData data = cache;
        data.setTotal_anti_bot_group_admin_actions(data.getTotal_anti_bot_group_admin_actions() + 1);
    }

    public static synchronized void callStartUp() {
        RuntimeData data = cache;
        data.setStartup_times(data.getStartup_times() + 1);
    }

    public static synchronized void callCommandExecuted() {
        RuntimeData data = cache;
        data.setTotal_command_received(data.getTotal_command_received() + 1);
    }

    public static synchronized void callLikeUser() {
        RuntimeData data = cache;
        data.setTotal_like_command_received(data.getTotal_like_command_received() + 1);
    }
}
