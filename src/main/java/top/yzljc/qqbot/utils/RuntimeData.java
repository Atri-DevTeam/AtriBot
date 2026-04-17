package top.yzljc.qqbot.utils;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName RuntimeData
 * @Created_at 2026/04/15
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
@Getter
@Setter
public class RuntimeData {
    private int startup_times;
    private int total_group_message_send;
    private int total_private_message_send;
    private int total_group_message_received;
    private int total_private_message_received;
    private int total_group_message_recalled;
    private int total_anti_bot_group_admin_actions;
    private int total_like_command_received;
    private int total_command_received;
    private Map<Long, GroupMessageData> group_message_data;

    @Getter
    @Setter
    public static class GroupMessageData {
        private String group_name;
        private long group_id;
        private int message_send;
        private int message_received;
    }

    public static RuntimeData createDefault() {
        RuntimeData data = new RuntimeData();
        data.setStartup_times(0);
        data.setTotal_group_message_send(0);
        data.setTotal_private_message_send(0);
        data.setTotal_group_message_received(0);
        data.setTotal_private_message_received(0);
        data.setTotal_group_message_recalled(0);
        data.setTotal_anti_bot_group_admin_actions(0);
        data.setTotal_like_command_received(0);
        data.setTotal_command_received(0);

        data.setGroup_message_data(new HashMap<>());

        return data;
    }

    public GroupMessageData getOrCreateGroup(long groupId, String groupName) {
        return group_message_data.computeIfAbsent(groupId, id -> {
            GroupMessageData group = new GroupMessageData();
            group.setGroup_id(id);
            group.setGroup_name(groupName);
            group.setMessage_send(0);
            group.setMessage_received(0);
            return group;
        });
    }
}