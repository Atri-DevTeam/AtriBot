package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.SseBroadcaster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.yzljc.atribot.webui.WebUiSupport.firstNonBlank;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseLong;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/** C2C 私聊 */
public class C2CController {

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
        Map<String, String> usernames = ChatContentRecord.findLatestKnownUsernames(openIds);
        List<C2CUserDTO> users = new ArrayList<>(all.size());
        for (var data : all) {
            users.add(toC2CUserDTO(data, usernames.get(data.userOpenId())));
        }
        ctx.json(Result.success(users));
    }

    public static void getC2CUserPermissions(Context ctx) {
        var data = OfficialUsers.getData(ctx.pathParam("userOpenId"));
        String username = ChatContentRecord.findLatestKnownUsername(data.userOpenId());
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
        ctx.json(Result.success(toC2CUserDTO(updated, ChatContentRecord.findLatestKnownUsername(updated.userOpenId()))));
    }

    public static void fetchC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(ChatContentRecord.fetchC2CMessages(userOpenId, page, pageSize)));
    }

    public static void clearC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        try {
            var result = ChatContentRecord.clearC2CMessages(userOpenId,
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
        String refContent = ctx.queryParam("refContent");
        String refAttachments = ctx.queryParam("refAttachments");
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        long excludeId = parseLong(ctx.queryParam("excludeId"), -1L);
        if (isBlank(msgIdx) && isBlank(refContent) && isBlank(refAttachments)) {
            ctx.json(Result.fail(400, "msgIdx 或引用内容不能为空"));
            return;
        }
        var result = ChatContentRecord.locateC2CMessageByReference(
                userOpenId, msgIdx, refAuthor, refContent, refAttachments, pageSize, excludeId);
        if (result == null) {
            ctx.json(Result.fail(404, "引用来源消息不存在或尚未记录"));
            return;
        }
        ctx.json(Result.success(result));
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
        String messageId;
        try {
            if (refId != null && !refId.isBlank()) {
                // 引用回复：发送带 message_reference 的主动消息，与群聊同逻辑
                if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); return; }
                messageId = C2CChat.refMessage(dto.getUserOpenId(), refId, dto.getContent());
                if (messageId != null) {
                    ChatContentRecord.patchC2CRefDisplayData(messageId,
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
                    messageId = C2CChat.replyMessage(dto.getUserOpenId(), replyId, image);
                } else {
                    if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); return; }
                    messageId = C2CChat.replyMessage(dto.getUserOpenId(), replyId, dto.getContent());
                }
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); yield null; }
                        yield C2CChat.sendMessage(dto.getUserOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                            ctx.status(400).json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                        }
                        ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                        ImageComponent image = ImageComponent.imageOf(dto.getImageValue(), type);
                        if (!isBlank(dto.getContent())) image.setText(dto.getContent());
                        yield C2CChat.sendMessage(dto.getUserOpenId(), image);
                    }
                    default -> {
                        if (isBlank(dto.getContent())) { ctx.status(400).json(Result.fail(400, "内容不能为空")); yield null; }
                        yield C2CChat.sendMessage(dto.getUserOpenId(), dto.getContent());
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
            String replyId = dto.getReplyMessageId();
            if (!isBlank(replyId)) {
                messageId = C2CChat.replyStreamDeltas(dto.getUserOpenId(), replyId, deltas);
            } else {
                messageId = C2CChat.streamDeltas(dto.getUserOpenId(), deltas);
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

    @Data
    public static class SendC2CStreamDTO {
        private String userOpenId;
        private String content;
        private String replyMessageId;
    }

    public record C2CUserDTO(String userOpenId, String role, java.util.Set<String> permissions,
                             boolean isBlocked, boolean isIgnored, boolean c2cPush, String username) {}

    @Data
    public static class SendC2CMessageDTO {
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
