package top.yzljc.qqbot.config.webui;

import io.javalin.http.Context;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.webui.exception.FeatureNotFoundException;
import top.yzljc.qqbot.functions.GroupContentRecord;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;

import java.util.*;

@Slf4j
public class WebUIController {

    public static void getGroupSettings(Context ctx) {
        long groupId = Long.parseLong(ctx.pathParam("groupId"));

        if (!GetGroupInfo.fetchAllGroupIds().contains(groupId)) {
            ctx.json(Result.fail(404, "群聊不在服务范围内"));
            return;
        }

        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (String feature : GroupConfigManager.getFeatureList()) {
            settings.put(feature, GroupConfigManager.isFeatureEnabled(groupId, feature));
        }

        GroupSettingsDTO dto = new GroupSettingsDTO();
        dto.setGroupId(groupId);
        dto.setFeatures(settings);

        ctx.json(Result.success(dto));
    }

    public static void setGroupSetting(Context ctx) {
        long groupId = Long.parseLong(ctx.pathParam("groupId"));
        ToggleFeatureDTO dto = ctx.bodyAsClass(ToggleFeatureDTO.class);

        if (!GroupConfigManager.getRegisteredFeatures().containsKey(dto.getFeature())) {
            throw new FeatureNotFoundException("未知的功能: " + dto.getFeature());
        }

        GroupConfigManager.setFeature(groupId, dto.getFeature(), dto.isEnabled());

        boolean newState = GroupConfigManager.isFeatureEnabled(groupId, dto.getFeature());
        dto.setEnabled(newState);

        SetFeatureResponseDTO result = new SetFeatureResponseDTO();
        result.setGroupId(groupId);
        result.setFeature(dto);

        ctx.json(Result.success(result));
    }

    public static void listGroups(Context ctx) {
        Map<Long, String> groupIds = new HashMap<>();
        for (long g : GetGroupInfo.fetchAllGroupIds()) {
            groupIds.put(g, GetGroupInfo.getGroupName(g));
        }
        if (groupIds.isEmpty()) {
            ctx.json(Result.fail(404, "群聊数据获取失败"));
            return;
        }
        ctx.json(Result.success(groupIds));
    }

    public static void fetchMessages(Context ctx) {
        MessageRequestDTO dto = ctx.bodyAsClass(MessageRequestDTO.class);
        if (!Config.getInstance().getMessageSpyGroups().contains(dto.groupId)) {
            ctx.json(Result.fail(404, "未开启该群的消息监听"));
            return;
        }
        ctx.json(Result.success(GroupContentRecord.fetchMessages(dto.groupId, dto.page)));
    }

    public static void recallMessage(Context ctx) {
        @SuppressWarnings("unchecked")
        Set<Integer> rawMessages = ctx.bodyAsClass(Set.class);
        Set<Long> messages = new HashSet<>();
        for (Object raw : rawMessages) {
            messages.add(((Number) raw).longValue());
        }
        for (long messageId : messages) {
            GroupMessage.recallMessage(messageId);
        }
        log.info("远程撤回消息成功：" + messages);
        ctx.json(Result.success("远程撤回消息成功：" + messages));
    }

    @Data
    public static class ToggleFeatureDTO {
        private String feature;
        private boolean enabled;
    }

    @Data
    public static class MessageRequestDTO {
        private long groupId;
        private int page;
    }

    @Data
    public static class SetFeatureResponseDTO {
        private long groupId;
        private ToggleFeatureDTO feature;
    }

    @Data
    public static class GroupSettingsDTO {
        private long groupId;
        private Map<String, Boolean> features;
    }

    @Data
    public static class GroupMessageDTO {
        private long userId;
        private String userName;
        private long messageId;
        private long time;
        private String content;
        private boolean isAdmin;
    }
}