package top.yzljc.atribot.webui.onebot;

import io.javalin.http.Context;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.napcat.GroupContentRecord;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.exception.FeatureNotFoundException;

import java.util.*;

@Slf4j
public class WebUIController {

    public static void getGroupSettings(Context ctx) {
        String groupId = ctx.pathParam("groupId");

        if (!GroupInformation.fetchAllGroupIds().contains(groupId)) {
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
        String groupId = ctx.pathParam("groupId");
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
        Map<String, String> groupIds = new HashMap<>();
        for (String g : GroupInformation.fetchAllGroupIds()) {
            groupIds.put(g, GroupInformation.getGroupName(g));
        }
        if (groupIds.isEmpty()) {
            ctx.json(Result.fail(404, "群聊数据获取失败"));
            return;
        }
        ctx.json(Result.success(groupIds));
    }

    public static void fetchMessages(Context ctx) {
        MessageRequestDTO dto = ctx.bodyAsClass(MessageRequestDTO.class);
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(dto.groupId)) {
            ctx.json(Result.fail(404, "未开启该群的消息监听"));
            return;
        }
        ctx.json(Result.success(GroupContentRecord.fetchMessages(Long.parseLong(dto.groupId), dto.page)));
    }

    public static void recallMessage(Context ctx) {
        @SuppressWarnings("unchecked")
        Set<Integer> rawMessages = ctx.bodyAsClass(Set.class);
        Set<Long> messages = new HashSet<>();
        for (Object raw : rawMessages) {
            messages.add(((Number) raw).longValue());
        }
        for (long messageId : messages) {
            GroupMessage.recallMessage(String.valueOf(messageId));
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
        private String groupId;
        private int page;
    }

    @Data
    public static class SetFeatureResponseDTO {
        private String groupId;
        private ToggleFeatureDTO feature;
    }

    @Data
    public static class GroupSettingsDTO {
        private String groupId;
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
