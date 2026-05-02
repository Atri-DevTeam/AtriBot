package top.yzljc.qqbot.config.webui;

import lombok.Data;
import org.springframework.web.bind.annotation.*;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.webui.exception.FeatureNotFoundException;
import top.yzljc.qqbot.functions.GroupContentRecord;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.utils.Logger;

import java.util.*;

@RestController
@RequestMapping("/webui/v1/atribot/settings")
public class WebUIController {

    @GetMapping("/get/{groupId}")
    public Result<GroupSettingsDTO> getGroupSettings(@PathVariable("groupId") long groupId) {

        if (!GetGroupInfo.fetchAllGroupIds().contains(groupId)) {
            return Result.fail(404, "群聊不在服务范围内");
        }

        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (String feature : GroupConfigManager.getFeatureList()) {
            settings.put(feature, GroupConfigManager.isFeatureEnabled(groupId, feature));
        }

        GroupSettingsDTO dto = new GroupSettingsDTO();
        dto.setGroupId(groupId);
        dto.setFeatures(settings);

        return Result.success(dto);
    }

    @PostMapping("/set/{groupId}")
    public Result<SetFeatureResponseDTO> setGroupSetting(@PathVariable("groupId") long groupId, @RequestBody ToggleFeatureDTO dto) {
        if (!GroupConfigManager.getRegisteredFeatures().containsKey(dto.getFeature())) {
            throw new FeatureNotFoundException("未知的功能: " + dto.getFeature());
        }

        GroupConfigManager.setFeature(groupId, dto.getFeature(), dto.isEnabled());

        boolean newState = GroupConfigManager.isFeatureEnabled(groupId, dto.getFeature());
        dto.setEnabled(newState);

        SetFeatureResponseDTO result = new SetFeatureResponseDTO();
        result.setGroupId(groupId);
        result.setFeature(dto);

        return Result.success(result);
    }

    @GetMapping("/listgroups")
    public Result<Map<Long, String>> fetchGroupList() {
        Map<Long, String> groupIds = new HashMap<>();
        for (long g : GetGroupInfo.fetchAllGroupIds()) {
            groupIds.put(g, GetGroupInfo.getGroupName(g));
        }
        if (groupIds.isEmpty()) return Result.fail(404, "群聊数据获取失败");
        return Result.success(groupIds);
    }

    @PostMapping("/fetchmessages")
    public Result<LinkedList<GroupMessageDTO>> fetchMessages(@RequestBody MessageRequestDTO dto) {
        if (!Config.getInstance().getMessageSpyGroups().contains(dto.groupId)) {
            return Result.fail(404, "未开启该群的消息监听");
        }
        return Result.success(GroupContentRecord.fetchMessages(dto.groupId, dto.page));
    }

    @PostMapping("/recallmessage")
    public Result<String> recallMessage(@RequestBody Set<Long> messages) {
        for (long messageId : messages) {
            GroupMessage.recallMessage(messageId);
        }
        Logger.info("远程撤回消息成功：" + messages);
        return Result.success("远程撤回消息成功：" + messages);
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