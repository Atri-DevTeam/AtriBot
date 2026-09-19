package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.function.tasks.QQChatContentRecord;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.SseBroadcaster;
import top.yzljc.atribot.webui.repo.OrphanedFriendRecordCleanup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.yzljc.atribot.webui.WebUiSupport.firstNonBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseArk23;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseLong;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/** C2C 私聊 */
public class C2CController {

    /** 扫描并清理已删除好友的私聊记录 */
    public static void startOrphanedFriendRecordCleanup(Context ctx) {
        ctx.json(Result.success(OrphanedFriendRecordCleanup.start()));
    }

    /** 查询好友记录清理进度 */
    public static void getOrphanedFriendRecordCleanupStatus(Context ctx) {
        ctx.json(Result.success(OrphanedFriendRecordCleanup.getStatus()));
    }

    public static void recallC2CMessage(Context ctx) {
        C2CRecallDTO dto = ctx.bodyAsClass(C2CRecallDTO.class);
        if (isBlank(dto.getUserOpenId()) || isBlank(dto.getMessageId())) {
            ctx.json(Result.fail(400, "userOpenId 和 messageId 不能为空"));
            return;
        }
        C2CChat.recallMessage(dto.getUserOpenId(), dto.getMessageId());
        ctx.json(Result.success("ok"));
    }

    public static void listC2CUsers(Context ctx) {
        List<OfficialUsers.UserData> all = OfficialUsers.listAll();
        Set<String> openIds = new LinkedHashSet<>();
        for (var data : all) {
            if (data.userOpenId() != null && !data.userOpenId().isBlank()) {
                openIds.add(data.userOpenId());
            }
        }
        Map<String, String> usernames = QQChatContentRecord.findLatestKnownUsernames(openIds);
        List<C2CUserDTO> users = new ArrayList<>(all.size());
        for (var data : all) {
            users.add(toC2CUserDTO(data, usernames.get(data.userOpenId())));
        }
        ctx.json(Result.success(users));
    }

    public static void getC2CUserPermissions(Context ctx) {
        var data = OfficialUsers.getData(ctx.pathParam("userOpenId"));
        String username = QQChatContentRecord.findLatestKnownUsername(data.userOpenId());
        ctx.json(Result.success(toC2CUserDTO(data, username)));
    }

    private static C2CUserDTO toC2CUserDTO(OfficialUsers.UserData data, String username) {
        return new C2CUserDTO(data.userOpenId(), data.role().name(), data.permissions(),
                data.isBlocked(), data.isIgnored(), data.c2cPush(), username);
    }

    public static void setC2CUserRole(Context ctx) {
        var data = OfficialUsers.getData(ctx.pathParam("userOpenId"));
        String role = ctx.queryParam("role");
        try {
            var r = role != null ? UnifiedRole.valueOf(role.toUpperCase()) : data.role();
            OfficialUsers.setPermissionGroup(ctx.pathParam("userOpenId"), r, data.permissions());
            ctx.json(Result.success("ok"));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, "无效的角色: " + role));
        }
    }

    public static void toggleC2CUserPermission(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        String perm = ctx.pathParam("permission");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        if (enabled) {
            OfficialUsers.addPermission(userOpenId, perm);
        } else {
            OfficialUsers.removePermission(userOpenId, perm);
        }
        ctx.json(Result.success("ok"));
    }

    public static void setC2CUserBlocked(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        boolean value = Boolean.parseBoolean(ctx.queryParam("value"));
        OfficialUsers.setBlocked(userOpenId, value);
        ctx.json(Result.success("ok"));
    }

    public static void setC2CUserIgnored(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        boolean value = Boolean.parseBoolean(ctx.queryParam("value"));
        OfficialUsers.setIgnored(userOpenId, value);
        ctx.json(Result.success("ok"));
    }

    public static void setC2CUserPush(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        boolean value = Boolean.parseBoolean(ctx.queryParam("value"));
        OfficialUsers.setC2CPush(userOpenId, value);
        SseBroadcaster.broadcastC2CPushStatus(userOpenId, value);
        ctx.json(Result.success("ok"));
    }

    public static void updateC2CUserProfile(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        UpdateC2CUserProfileDTO dto = ctx.bodyAsClass(UpdateC2CUserProfileDTO.class);
        if (dto == null) {
            dto = new UpdateC2CUserProfileDTO();
        }
        if (isBlank(userOpenId)) {
            ctx.status(400).json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }

        UnifiedRole role = OfficialUsers.getData(userOpenId).role();
        if (!isBlank(dto.getRole())) {
            try {
                role = UnifiedRole.valueOf(dto.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                ctx.json(Result.fail(400, "无效的角色: " + dto.getRole()));
                return;
            }
        }

        java.util.Set<String> permissions = new java.util.LinkedHashSet<>();
        if (dto.getPermissions() != null) {
            for (String perm : dto.getPermissions()) {
                if (perm != null && !perm.trim().isBlank()) {
                    permissions.add(perm.trim());
                }
            }
        }

        boolean blocked = dto.isBlocked();
        boolean ignored = dto.isIgnored();
        boolean c2cPush = dto.isC2cPush();
        boolean pushChanged = OfficialUsers.getData(userOpenId).c2cPush() != c2cPush;

        OfficialUsers.setPermissionGroup(userOpenId, role, permissions);
        OfficialUsers.setBlocked(userOpenId, blocked);
        OfficialUsers.setIgnored(userOpenId, ignored);
        OfficialUsers.setC2CPush(userOpenId, c2cPush);
        if (pushChanged) {
            SseBroadcaster.broadcastC2CPushStatus(userOpenId, c2cPush);
        }

        var updated = OfficialUsers.getData(userOpenId);
        ctx.json(Result.success(toC2CUserDTO(updated, QQChatContentRecord.findLatestKnownUsername(updated.userOpenId()))));
    }

    public static void fetchC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(QQChatContentRecord.fetchC2CMessages(userOpenId, page, pageSize)));
    }

    public static void clearC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        try {
            var result = QQChatContentRecord.clearC2CMessages(userOpenId,
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

    /** 私聊引用来源定位，与 {@link GroupController#locateGroupMessageByRefIdx} 同构 */
    public static void locateC2CMessageByRefIdx(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
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
        var result = QQChatContentRecord.locateC2CMessageByReference(
                userOpenId, msgIdx, refAuthor, refContent, refAttachments, pageSize, excludeId);
        if (result == null) {
            ctx.json(Result.fail(404, "引用来源消息不存在或尚未记录"));
            return;
        }
        ctx.json(Result.success(result));
    }

    /**
     * 发送持续 60 秒的单聊正在输入通知
     *
     * @param ctx 请求上下文，路径参数 userOpenId 指定通知接收者
     */
    public static void sendC2CInputNotify(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        if (isBlank(userOpenId)) {
            ctx.status(400).json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        if (C2CChat.sendInputNotify(userOpenId)) {
            ctx.json(Result.success(true));
        } else {
            ctx.status(502).json(Result.fail(502, "输入状态通知发送失败，请检查暂停状态或发送日志"));
        }
    }

    public static void sendC2CMessage(Context ctx) {
        SendC2CMessageDTO dto = ctx.bodyAsClass(SendC2CMessageDTO.class);
        if (isBlank(dto.getUserOpenId())) {
            ctx.status(400).json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        String msgType = dto.getMsgType() != null ? dto.getMsgType() : "text";
        String replyId = dto.getReplyMessageId();
        String refId = dto.getRefMessageId();
        if (dto.isWakeup() && (!isBlank(replyId) || !isBlank(refId))) {
            ctx.status(400).json(Result.fail(400, "召回消息不能同时指定被动回复来源或引用"));
            return;
        }
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
                messageId = dto.isWakeup()
                        ? C2CChat.wakeupMessage(dto.getUserOpenId(), ark)
                        : C2CChat.sendMessage(dto.getUserOpenId(), ark);
            } else if (refId != null && !refId.isBlank()) {
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                    if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                    messageId = isBlank(replyId)
                            ? C2CChat.refMessage(dto.getUserOpenId(), refId, image)
                            : C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), image, refId);
                } else {
                    if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); return; }
                    if ("markdown".equals(msgType)) {
                        Markdown markdown = new Markdown(dto.getContent());
                        messageId = isBlank(replyId)
                                ? C2CChat.refMessage(dto.getUserOpenId(), refId, markdown)
                                : C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), markdown, null, refId);
                    } else {
                        messageId = isBlank(replyId)
                                ? C2CChat.refMessage(dto.getUserOpenId(), refId, dto.getContent())
                                : C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), dto.getContent(), refId);
                    }
                }
                if (messageId != null) {
                    QQChatContentRecord.patchC2CRefDisplayData(messageId,
                            dto.getRefAuthor(), dto.getRefContent(), dto.getRefAttachments(), refId);
                }
            } else if (replyId != null && !replyId.isBlank()) {
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                    if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                    messageId = C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), image);
                } else {
                    if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); return; }
                    messageId = "markdown".equals(msgType)
                            ? C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), new Markdown(dto.getContent()))
                            : C2CChat.replyMessage(dto.getUserOpenId(), RT.message(replyId), dto.getContent());
                }
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); yield null; }
                        yield dto.isWakeup()
                                ? C2CChat.wakeupMessage(dto.getUserOpenId(), new Markdown(dto.getContent()))
                                : C2CChat.sendMessage(dto.getUserOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                            ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                        }
                        ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                        ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                        if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                        yield dto.isWakeup()
                                ? C2CChat.wakeupMessage(dto.getUserOpenId(), image)
                                : C2CChat.sendMessage(dto.getUserOpenId(), image);
                    }
                    default -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); yield null; }
                        yield dto.isWakeup()
                                ? C2CChat.wakeupMessage(dto.getUserOpenId(), dto.getContent())
                                : C2CChat.sendMessage(dto.getUserOpenId(), dto.getContent());
                    }
                };
            }
        } catch (QQMessageSendException e) {
            ctx.status(502).json(Result.fail(502, e.getMessage()));
            return;
        } catch (Exception e) {
            ctx.status(500).json(Result.fail(500, "发送失败"));
            return;
        }
        if (messageId == null) {
            ctx.status(502).json(Result.fail(502, "发送失败：官方接口未返回消息ID"));
            return;
        }
        ctx.json(Result.success(new GroupController.SendGroupMessageResponse(messageId)));
    }

    public static void sendC2CStreamMessage(Context ctx) {
        SendC2CStreamDTO dto = ctx.bodyAsClass(SendC2CStreamDTO.class);
        if (isBlank(dto.getUserOpenId())) {
            ctx.status(400).json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        if (!isBlank(dto.getRefMessageId())) {
            ctx.status(400).json(Result.fail(400, "流式消息暂不支持引用"));
            return;
        }
        if (dto.isWakeup() && !isBlank(dto.getReplyMessageId())) {
            ctx.status(400).json(Result.fail(400, "召回消息不能同时指定被动回复来源"));
            return;
        }
        if (!dto.isWakeup() && isBlank(dto.getReplyMessageId())) {
            ctx.status(400).json(Result.fail(400, "流式消息需要指定被动回复的来源消息，或开启召回"));
            return;
        }
        if (isBlank(dto.getContent())) {
            ctx.status(400).json(Result.fail(400, "内容不能为空"));
            return;
        }
        List<Markdown> deltas = java.util.Arrays.stream(dto.getContent().split("\n"))
                .filter(s -> !s.isBlank())
                .map(Markdown::new)
                .toList();
        if (deltas.isEmpty()) {
            ctx.status(400).json(Result.fail(400, "内容不能为空"));
            return;
        }
        String messageId;
        try {
            messageId = dto.isWakeup()
                    ? C2CChat.wakeupStreamDeltas(dto.getUserOpenId(), deltas)
                    : C2CChat.replyStreamDeltas(dto.getUserOpenId(), RT.message(dto.getReplyMessageId()), deltas);
        } catch (QQMessageSendException e) {
            ctx.status(502).json(Result.fail(502, e.getMessage()));
            return;
        } catch (Exception e) {
            ctx.status(500).json(Result.fail(500, "发送失败"));
            return;
        }
        if (messageId == null) {
            ctx.status(502).json(Result.fail(502, "发送失败：官方接口未返回消息ID"));
            return;
        }
        ctx.json(Result.success(new GroupController.SendGroupMessageResponse(messageId)));
    }

    @Data
    public static class SendC2CStreamDTO {
        private boolean wakeup;
        private String userOpenId;
        private String content;
        private String replyMessageId;
        private String refMessageId;
    }

    public record C2CUserDTO(String userOpenId, String role, java.util.Set<String> permissions,
                             boolean isBlocked, boolean isIgnored, boolean c2cPush, String username) {}

    @Data
    public static class SendC2CMessageDTO {
        private boolean wakeup;
        private JsonNode ark;
        private String userOpenId;
        private String msgType;
        private String content;
        private String imageType;
        private String imageValue;
        private String replyMessageId;
        private String refMessageId;
        private String refAuthor;
        private String refContent;
        private String refAttachments;
    }

    @Data
    public static class UpdateC2CUserProfileDTO {
        private String role;
        private java.util.List<String> permissions;
        private boolean blocked;
        private boolean ignored;
        private boolean c2cPush;
    }

    @Data
    public static class C2CRecallDTO {
        private String userOpenId;
        private String messageId;
    }
}
