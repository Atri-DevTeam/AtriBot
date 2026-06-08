package top.yzljc.atribot.webui.official;

import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.ImageType;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.functions.official.ChatContentRecord;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.functions.official.permission.C2CList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OfficialWebUIController {

    public static void listGroups(Context ctx) {
        List<GroupDTO> groups = GroupList.listGroups().stream()
                .sorted(Comparator.comparing(GroupList.GroupData::timestamp).reversed())
                .map(data -> new GroupDTO(
                        data.groupOpenId(),
                        data.opMemberOpenId(),
                        data.timestamp(),
                        data.isWhitelist(),
                        data.isBlacklisted(),
                        data.isAllowedActive(),
                        data.realGroupId()
                ))
                .toList();
        ctx.json(Result.success(groups));
    }

    public static void fetchGroupMessages(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(ChatContentRecord.fetchGroupMessages(groupOpenId, page, pageSize)));
    }

    public static void sendGroupMessage(Context ctx) {
        SendGroupMessageDTO dto = ctx.bodyAsClass(SendGroupMessageDTO.class);
        if (dto.getGroupOpenId() == null || dto.getGroupOpenId().isBlank()) {
            ctx.json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }

        String msgType = dto.getMsgType() != null ? dto.getMsgType() : "text";
        String messageId;

        try {
            messageId = switch (msgType) {
                case "markdown" -> {
                    if (isBlank(dto.getContent())) {
                        ctx.json(Result.fail(400, "Markdown 内容不能为空"));
                        yield null;
                    }
                    yield GroupChat.sendMessage(dto.getGroupOpenId(), new Markdown(dto.getContent()));
                }
                case "image" -> {
                    String imageType = dto.getImageType();
                    String imageValue = dto.getImageValue();
                    if (isBlank(imageType) || isBlank(imageValue)) {
                        ctx.json(Result.fail(400, "图片类型和内容不能为空"));
                        yield null;
                    }
                    yield GroupChat.sendMessage(dto.getGroupOpenId(),
                            "base64".equalsIgnoreCase(imageType) ? ImageType.BASE64 : ImageType.URL,
                            imageValue);
                }
                default -> {
                    if (isBlank(dto.getContent())) {
                        ctx.json(Result.fail(400, "消息内容不能为空"));
                        yield null;
                    }
                    yield GroupChat.sendMessage(dto.getGroupOpenId(), dto.getContent());
                }
            };
        } catch (Exception e) {
            ctx.json(Result.fail(500, "消息发送失败: " + e.getMessage()));
            return;
        }

        if (messageId == null) return; // error already set
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static void getConfig(Context ctx) {
        ctx.json(Result.success(new ConfigDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId(),
                Config.getInstance().getOfficialUsername()
        )));
    }

    public static void verifyToken(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            ctx.cookie("webui_token", auth.substring(7), 1440); // 24h
        }
        ctx.json(Result.success("ok"));
    }

    public record ConfigDTO(String appId, String botOpenId, String botName) {
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public record GroupDTO(String groupOpenId, String opMemberOpenId, long timestamp,
                           boolean whitelist, boolean blacklisted, boolean allowedActive, Long realGroupId) {
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
        private String groupOpenId;
        private String msgType;   // "text" | "markdown" | "image"
        private String content;
        private String imageType; // "url" | "base64" (仅 image 时)
        private String imageValue;// 图片 URL 或 base64 (仅 image 时)
    }

    // ═══════════════ C2C 私聊 ═══════════════

    public static void listC2CUsers(Context ctx) {
        List<C2CUserDTO> users = new ArrayList<>();
        for (var data : C2CList.listAll()) {
            users.add(new C2CUserDTO(data.userOpenId(), data.role().name()));
        }
        ctx.json(Result.success(users));
    }

    public static void fetchC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(ChatContentRecord.fetchC2CMessages(userOpenId, page, pageSize)));
    }

    public static void sendC2CMessage(Context ctx) {
        SendC2CMessageDTO dto = ctx.bodyAsClass(SendC2CMessageDTO.class);
        if (isBlank(dto.getUserOpenId())) {
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        String msgType = dto.getMsgType() != null ? dto.getMsgType() : "text";
        String messageId;
        try {
            messageId = switch (msgType) {
                case "markdown" -> {
                    if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); yield null; }
                    yield Atri.getInstance().getChatService()
                            .sendActivePrivateMarkdownMessage(dto.getUserOpenId(), new Markdown(dto.getContent()));
                }
                case "image" -> {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                    }
                    yield Atri.getInstance().getChatService()
                            .sendActivePrivateImageMessage(dto.getUserOpenId(),
                                    "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL,
                                    dto.getImageValue());
                }
                default -> {
                    if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); yield null; }
                    yield Atri.getInstance().getChatService()
                            .sendActivePrivateTextMessage(dto.getUserOpenId(), dto.getContent());
                }
            };
        } catch (Exception e) {
            ctx.json(Result.fail(500, "发送失败: " + e.getMessage()));
            return;
        }
        if (messageId == null) return;
        ctx.json(Result.success(new SendGroupMessageResponse(messageId)));
    }

    public record C2CUserDTO(String userOpenId, String role) {}

    @Data
    public static class SendC2CMessageDTO {
        private String userOpenId;
        private String msgType;
        private String content;
        private String imageType;
        private String imageValue;
    }
}
