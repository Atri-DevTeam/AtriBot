package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.management.Mute;
import top.yzljc.atribot.chat.official.management.GroupMember;
import top.yzljc.atribot.chat.official.management.GroupMemberBlacklist;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.function.tasks.QQChatContentRecord;
import top.yzljc.atribot.function.command.PushTaskCommand;
import top.yzljc.atribot.function.tasks.pushtask.PushTask;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.repo.ChatPinnedRepo;
import top.yzljc.atribot.webui.repo.OrphanedGroupRecordCleanup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static top.yzljc.atribot.webui.WebUiSupport.firstNonBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseArk23;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseLong;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/** 群聊、会话、群管理、消息收发 */
public class GroupController {

    public static void listGroups(Context ctx) {
        List<GroupDTO> groups = OfficialGroups.listGroups().stream()
                .sorted(Comparator.comparing(OfficialGroups.GroupData::joinedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .map(GroupController::toGroupDTO)
                .toList();
        ctx.json(Result.success(groups));
    }

    /** 「聊天」页会话列表：群聊 + 私聊合并，各带最后一条消息预览。置顶会话始终前置并从分页排除。 */
    public static void listChatConversations(Context ctx) {
        int limit = parseInt(ctx.queryParam("limit"), 100);
        int offset = parseInt(ctx.queryParam("offset"), 0);

        List<String> pinnedKeys = ChatPinnedRepo.list();
        Set<String> pinnedGroups = new LinkedHashSet<>();
        Set<String> pinnedC2c = new LinkedHashSet<>();
        for (String key : pinnedKeys) {
            int colon = key.indexOf(':');
            if (colon > 0 && colon < key.length() - 1) {
                String type = key.substring(0, colon);
                String openId = key.substring(colon + 1);
                if ("group".equals(type)) pinnedGroups.add(openId);
                else if ("c2c".equals(type)) pinnedC2c.add(openId);
            }
        }

        // 置顶会话单独取最新一条消息并前置（按置顶顺序），分页里排除它们避免重复
        List<QQChatContentRecord.ConversationRecord> pinned = new ArrayList<>();
        for (String groupOpenId : pinnedGroups) {
            var c = QQChatContentRecord.fetchConversation("group", groupOpenId);
            if (c != null) pinned.add(c);
        }
        for (String userOpenId : pinnedC2c) {
            var c = QQChatContentRecord.fetchConversation("c2c", userOpenId);
            if (c != null) pinned.add(c);
        }

        var page = QQChatContentRecord.fetchConversations(limit, offset, pinnedGroups, pinnedC2c);
        List<QQChatContentRecord.ConversationRecord> items = new ArrayList<>(pinned);
        items.addAll(page.items());
        ctx.json(Result.success(new ConversationListResult(items, page.hasMore())));
    }

    public record ConversationListResult(List<QQChatContentRecord.ConversationRecord> items, boolean hasMore) {
    }

    public static void listChatPinned(Context ctx) {
        ctx.json(Result.success(ChatPinnedRepo.list()));
    }

    public static void setChatPinned(Context ctx) {
        ChatPinnedDTO dto = ctx.bodyAsClass(ChatPinnedDTO.class);
        if (isBlank(dto.getKey())) {
            ctx.json(Result.fail(400, "key 不能为空"));
            return;
        }
        ChatPinnedRepo.setPinned(dto.getKey(), dto.isPinned());
        ctx.json(Result.success(ChatPinnedRepo.list()));
    }

    @Data
    public static class ChatPinnedDTO {
        private String key;
        private boolean pinned;
    }

    public static void fetchGroupMessages(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(QQChatContentRecord.fetchGroupMessages(groupOpenId, page, pageSize)));
    }

    public static void clearGroupMessages(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        try {
            var result = QQChatContentRecord.clearGroupMessages(groupOpenId,
                    body == null ? "all" : body.path("mode").asText("all"),
                    body == null ? 0 : body.path("count").asInt(0),
                    body == null ? null : body.path("start").asText(null),
                    body == null ? null : body.path("end").asText(null));
            ctx.json(Result.success(result));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
        } catch (IllegalStateException e) {
            ctx.json(Result.fail(500, e.getMessage()));
        }
    }

    public static void startOrphanedGroupRecordCleanup(Context ctx) {
        ctx.json(Result.success(OrphanedGroupRecordCleanup.start()));
    }

    public static void getOrphanedGroupRecordCleanupStatus(Context ctx) {
        ctx.json(Result.success(OrphanedGroupRecordCleanup.getStatus()));
    }

    /** 群成员列表，当前按本群发言记录聚合 */
    public static void listGroupMembers(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(QQChatContentRecord.fetchGroupMembers(groupOpenId)));
    }

    /** 查询群成员详情，同时返回机器人在本群是否具有管理身份 */
    public static void getGroupMemberInfo(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String memberOpenId = ctx.pathParam("memberOpenId");
        if (isBlank(groupOpenId) || isBlank(memberOpenId)) {
            ctx.status(400).json(Result.fail(400, "groupOpenId 或 memberOpenId 不能为空"));
            return;
        }
        var info = GroupMember.getMemberInfo(groupOpenId, memberOpenId);
        // 成员资料查询失败不影响本地已知的机器人管理身份。
        String infoError = info == null ? "查询成员详情失败，请检查官方接口响应" : null;
        ctx.json(Result.success(new GroupMemberInfoDTO(info, canManageGroupMembers(groupOpenId), infoError)));
    }

    /** 踢出单个群成员，可选择同时加入群黑名单 */
    public static void removeGroupMember(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String memberOpenId = ctx.pathParam("memberOpenId");
        if (isBlank(groupOpenId) || isBlank(memberOpenId)) {
            ctx.status(400).json(Result.fail(400, "groupOpenId 或 memberOpenId 不能为空"));
            return;
        }
        if (!canManageGroupMembers(groupOpenId)) {
            ctx.status(403).json(Result.fail(403, "机器人不是本群管理员，无法踢出成员"));
            return;
        }
        JsonNode body = ctx.body().isBlank() ? null : ctx.bodyAsClass(JsonNode.class);
        boolean addToMemberBlacklist = body != null && body.path("addToMemberBlacklist").asBoolean(false);
        var result = GroupMember.removeMembersDetailed(groupOpenId, List.of(memberOpenId), addToMemberBlacklist);
        if (result == null || !"success".equals(result.removeMembersResult())) {
            ctx.status(502).json(Result.fail(502, "踢出成员失败，请检查官方接口响应"));
            return;
        }
        // 移除成功但拉黑失败时保留详细结果，前端分别提示。
        ctx.json(Result.success(result));
    }

    private static boolean canManageGroupMembers(String groupOpenId) {
        var group = OfficialGroups.getData(groupOpenId);
        if (group == null || group.memberRole() == null) return false;
        String role = group.memberRole().name();
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }

    public record GroupMemberInfoDTO(GroupMember.MemberInfo memberInfo, boolean canManage, String memberInfoError) {}

    /** 分页查询群成员黑名单 */
    public static void getGroupMemberBlacklist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        if (!canManageGroupMembers(groupOpenId)) {
            ctx.status(403).json(Result.fail(403, "机器人不是本群管理员，无法查询群黑名单"));
            return;
        }
        var result = GroupMemberBlacklist.getMemberBlacklist(groupOpenId,
                ctx.queryParam("cursor"), parseInt(ctx.queryParam("limit"), 20));
        if (result == null) {
            ctx.status(502).json(Result.fail(502, "查询群黑名单失败，请检查官方接口响应"));
            return;
        }
        ctx.json(Result.success(result));
    }

    /** 将指定成员加入群黑名单，目标成员必须已不在群内 */
    public static void addGroupMemberBlacklist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        if (!canManageGroupMembers(groupOpenId)) {
            ctx.status(403).json(Result.fail(403, "机器人不是本群管理员，无法添加群黑名单"));
            return;
        }
        JsonNode body = ctx.body().isBlank() ? null : ctx.bodyAsClass(JsonNode.class);
        String memberOpenId = body == null ? null : body.path("memberOpenId").asText(null);
        if (isBlank(memberOpenId)) {
            ctx.status(400).json(Result.fail(400, "memberOpenId 不能为空"));
            return;
        }
        var result = GroupMemberBlacklist.setMemberBlacklistDetailed(groupOpenId,
                GroupMemberBlacklist.Op.ADD, List.of(memberOpenId));
        if (result == null || !result.failOpenIds().isEmpty()) {
            ctx.status(502).json(Result.fail(502, "添加群黑名单失败，请检查官方接口响应"));
            return;
        }
        ctx.json(Result.success(null));
    }

    public static void getGroupMuteState(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        if (groupOpenId == null || groupOpenId.isBlank()) {
            ctx.json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }
        var state = Mute.queryMuteState(groupOpenId);
        if (state == null) {
            ctx.json(Result.fail(500, "查询群禁言状态失败"));
            return;
        }
        ctx.json(Result.success(state));
    }

    public static void muteGroupMember(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String memberOpenId = body != null ? body.path("memberOpenId").asText(null) : null;
        long seconds = body != null ? body.path("seconds").asLong(3600) : 3600;
        if (groupOpenId == null || groupOpenId.isBlank() || memberOpenId == null || memberOpenId.isBlank()) {
            ctx.json(Result.fail(400, "groupOpenId 或 memberOpenId 不能为空"));
            return;
        }
        if (seconds <= 0) {
            ctx.json(Result.fail(400, "禁言时长必须大于 0"));
            return;
        }
        boolean ok = Mute.muteMember(groupOpenId, memberOpenId, Duration.ofSeconds(seconds));
        if (!ok) {
            ctx.json(Result.fail(500, "设置禁言失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    /** 解除指定群成员禁言（对应官方接口 op=del） */
    public static void unmuteGroupMember(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String memberOpenId = body != null ? body.path("memberOpenId").asText(null) : null;
        if (groupOpenId == null || groupOpenId.isBlank() || memberOpenId == null || memberOpenId.isBlank()) {
            ctx.json(Result.fail(400, "groupOpenId 或 memberOpenId 不能为空"));
            return;
        }
        boolean ok = Mute.unmuteMember(groupOpenId, memberOpenId);
        if (!ok) {
            ctx.json(Result.fail(500, "解除禁言失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    public static void locateGroupMessageByRefIdx(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String msgIdx = firstNonBlank(ctx.queryParam("msgIdx"), ctx.queryParam("refIdx"));
        String refAuthor = ctx.queryParam("refAuthor");
        String refContent = ctx.queryParam("content");
        String refAttachments = ctx.queryParam("refAttachments");
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        long excludeId = parseLong(ctx.queryParam("excludeId"), -1L);
        if (isBlank(msgIdx) && isBlank(refContent) && isBlank(refAttachments)) {
            ctx.json(Result.fail(400, "msgIdx 或引用内容不能为空"));
            return;
        }
        var result = QQChatContentRecord.locateGroupMessageByReference(
                groupOpenId, msgIdx, refAuthor, refContent, refAttachments, pageSize, excludeId);
        if (result == null) {
            ctx.json(Result.fail(404, "引用来源消息不存在或尚未记录"));
            return;
        }
        ctx.json(Result.success(result));
    }

    public static void sendGroupMessage(Context ctx) {
        SendGroupMessageDTO dto = ctx.bodyAsClass(SendGroupMessageDTO.class);
        if (dto.getGroupOpenId() == null || dto.getGroupOpenId().isBlank()) {
            ctx.status(400).json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }

        String msgType = dto.getMsgType() != null ? dto.getMsgType() : "text";
        String replyId = dto.getReplyMessageId();
        String refId = dto.getRefMessageId();
        String messageId;

        try {
            if ("ark".equals(msgType)) {
                if (!isBlank(replyId) || !isBlank(refId)) {
                    ctx.status(400).json(Result.fail(400, "Ark 仅支持主动发送，请关闭被动消息和引用")); return;
                }
                Ark23 ark;
                try {
                    ark = parseArk23(dto.getArk());
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(Result.fail(400, e.getMessage())); return;
                }
                messageId = GroupChat.sendMessage(dto.getGroupOpenId(), ark);
            } else if (refId != null && !refId.isBlank()) {
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                    if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                    messageId = isBlank(replyId)
                            ? GroupChat.refMessage(dto.getGroupOpenId(), refId, image)
                            : GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), image, refId);
                } else {
                    if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "消息内容不能为空")); return; }
                    if ("markdown".equals(msgType)) {
                        Markdown markdown = new Markdown(dto.getContent());
                        messageId = isBlank(replyId)
                                ? GroupChat.refMessage(dto.getGroupOpenId(), refId, markdown)
                                : GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), markdown, null, refId);
                    } else {
                        messageId = isBlank(replyId)
                                ? GroupChat.refMessage(dto.getGroupOpenId(), refId, dto.getContent())
                                : GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), dto.getContent(), refId);
                    }
                }
                if (messageId != null) {
                    QQChatContentRecord.patchRefDisplayData(messageId,
                            dto.getRefAuthor(), dto.getRefContent(), dto.getRefAttachments(), refId);
                }
            } else if (!isBlank(replyId)) {
                // 被动回复
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                    if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                    messageId = GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), image);
                } else {
                    if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "消息内容不能为空")); return; }
                    messageId = "markdown".equals(msgType)
                            ? GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), new Markdown(dto.getContent()))
                            : GroupChat.replyMessage(dto.getGroupOpenId(), RT.message(replyId), dto.getContent());
                }
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "Markdown 内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                            ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                        }
                        ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                        ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                        if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), image);
                    }
                    default -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "消息内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), dto.getContent());
                    }
                };
            }
        } catch (QQMessageSendException e) {
            ctx.status(502).json(Result.fail(502, e.getMessage()));
            return;
        } catch (Exception e) {
            ctx.status(500).json(Result.fail(500, "消息发送失败"));
            return;
        }

        if (messageId == null) {
            ctx.status(502).json(Result.fail(502, "消息发送失败：官方接口未返回消息ID"));
            return;
        }
        ctx.json(Result.success(new SendGroupMessageResponse(messageId)));
    }

    public static void recallMessage(Context ctx) {
        RecallDTO dto = ctx.bodyAsClass(RecallDTO.class);
        if (isBlank(dto.getGroupOpenId()) || isBlank(dto.getMessageId())) {
            ctx.json(Result.fail(400, "groupOpenId 和 messageId 不能为空"));
            return;
        }
        GroupChat.recallMessage(dto.getGroupOpenId(), dto.getMessageId());
        ctx.json(Result.success("ok"));
    }

    public static void getGroupFunctions(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ObjectNode config = OfficialGroups.getRawFunctionConfig(groupOpenId);
        for (PushTask task : PushTaskCommand.getTasks()) {
            if (!task.isDefaultEnabled() || hasExplicitEnabled(config, task.getFunctionId())) continue;
            config.putObject(task.getFunctionId()).put("enabled", true);
        }
        ctx.json(Result.success(config));
    }

    public static void listGroupFunctionKeys(Context ctx) {
        TreeSet<String> keys = new TreeSet<>();
        PushTaskCommand.getTasks().forEach(task -> keys.add(task.getFunctionId()));
        for (var group : OfficialGroups.listGroups()) {
            var config = OfficialGroups.getRawFunctionConfig(group.groupOpenId());
            config.fieldNames().forEachRemaining(keys::add);
        }
        ctx.json(Result.success(List.copyOf(keys)));
    }

    public static void setGroupWhitelist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        OfficialGroups.setWhitelist(groupOpenId, enabled);
        ctx.json(Result.success("ok"));
    }

    public static void setGroupBlacklist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        OfficialGroups.setGroupBlacklisted(groupOpenId, enabled);
        ctx.json(Result.success("ok"));
    }

    public static void setGroupRealGroupId(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String value = ctx.queryParam("value");
        Long realGroupId = (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) ? null : Long.parseLong(value);
        OfficialGroups.setRealGroupId(groupOpenId, realGroupId);
        ctx.json(Result.success("ok"));
    }

    public static void syncGroupProfile(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        var profile = QQBot.fetchGroupProfile(groupOpenId);
        if (profile == null) {
            ctx.json(Result.fail(502, "同步群资料失败，请检查官方接口响应"));
            return;
        }
        if (!OfficialGroups.saveGroupProfile(profile)) {
            ctx.json(Result.fail(500, "保存群资料失败"));
            return;
        }
        ctx.json(Result.success(toGroupDTO(OfficialGroups.getData(groupOpenId))));
    }

    public static void setGroupFunction(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String functionKey = ctx.pathParam("functionKey");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        OfficialGroups.setFunctionEnabled(groupOpenId, functionKey, enabled, "webui");
        ctx.json(Result.success("ok"));
    }

    public record GroupDTO(String groupOpenId, String opMemberOpenId, String joinedAt,
                           boolean whitelist, boolean blacklisted, boolean allowProactiveMsg, Long realGroupId,
                           String memberOpenid, String recvMsgSetting, String memberRole, String groupName,
                           String groupFingerMemo, String groupClassText, List<String> groupTags, int groupMemberNum,
                           List<String> enabledFunctions) {
    }

    private static GroupDTO toGroupDTO(OfficialGroups.GroupData data) {
        return new GroupDTO(
                data.groupOpenId(),
                data.opMemberOpenId(),
                data.joinedAt(),
                data.isWhitelist(),
                data.isBlacklisted(),
                data.allowProactiveMsg(),
                data.realGroupId(),
                data.memberOpenid(),
                data.recvMsgSetting() == null ? null : data.recvMsgSetting().getJsonValue(),
                data.memberRole() == null ? null : data.memberRole().name(),
                data.groupName(),
                data.groupFingerMemo(),
                data.groupClassText(),
                data.groupTags(),
                data.groupMemberNum(),
                enabledFunctions(data.groupOpenId())
        );
    }

    /** 群已启用的功能键列表（按功能分类筛选用） */
    private static List<String> enabledFunctions(String groupOpenId) {
        ObjectNode config = OfficialGroups.getRawFunctionConfig(groupOpenId);
        TreeSet<String> keys = new TreeSet<>();
        config.fieldNames().forEachRemaining(key -> {
            JsonNode funcNode = config.get(key);
            if (hasExplicitEnabled(config, key) && funcNode.get("enabled").asBoolean()) {
                keys.add(key);
            }
        });
        for (PushTask task : PushTaskCommand.getTasks()) {
            if (task.isDefaultEnabled() && !hasExplicitEnabled(config, task.getFunctionId())) {
                keys.add(task.getFunctionId());
            }
        }
        return List.copyOf(keys);
    }

    private static boolean hasExplicitEnabled(ObjectNode config, String functionKey) {
        JsonNode funcNode = config.get(functionKey);
        if (funcNode == null || !funcNode.isObject()) {
            return false;
        }
        JsonNode enabledNode = funcNode.get("enabled");
        return enabledNode != null && enabledNode.isBoolean();
    }

    public record SendGroupMessageResponse(String messageOpenId) {
    }

    @Data
    public static class RecallDTO {
        private String groupOpenId;
        private String messageId;
    }

    @Data
    public static class SendGroupMessageDTO {
        private JsonNode ark;
        private String groupOpenId;
        private String msgType;   // "text" | "markdown" | "image"
        private String content;
        private String imageType; // "url" | "base64" (仅 image 时)
        private String imageValue;// 图片 URL 或 base64 (仅 image 时)
        private String replyMessageId;
        private String refMessageId;
        private String refAuthor;
        private String refContent;
        private String refAttachments;
    }
}
