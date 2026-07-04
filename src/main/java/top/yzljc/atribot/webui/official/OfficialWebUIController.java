package top.yzljc.atribot.webui.official;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.PermissionRole;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.webui.Result;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OfficialWebUIController {

    public static void listGroups(Context ctx) {
        List<GroupDTO> groups = OfficialGroups.listGroups().stream()
                .sorted(Comparator.comparing(OfficialGroups.GroupData::timestamp).reversed())
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
            String refId = dto.getRefMessageId();
            if (refId != null && !refId.isBlank()) {
                // 引用回复：发送带 message_reference 的主动消息
                if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "消息内容不能为空")); return; }
                messageId = GroupChat.refMessage(dto.getGroupOpenId(), refId, dto.getContent());
                if (messageId != null) {
                    ChatContentRecord.patchRefDisplayData(messageId,
                            dto.getRefAuthor(), dto.getRefContent(), dto.getRefAttachments());
                }
            } else if (dto.getReplyMessageId() != null && !dto.getReplyMessageId().isBlank()) {
                // 被动回复
                if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "消息内容不能为空")); return; }
                messageId = GroupChat.replyMessage(dto.getGroupOpenId(), dto.getReplyMessageId(), dto.getContent());
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "Markdown 内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        String imageType = dto.getImageType(); String imageValue = dto.getImageValue();
                        if (isBlank(imageType) || isBlank(imageValue)) { ctx.json(Result.fail(400, "图片类型和内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(),
                                "base64".equalsIgnoreCase(imageType) ? ImageType.BASE64 : ImageType.URL, imageValue);
                    }
                    default -> {
                        if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "消息内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), dto.getContent());
                    }
                };
            }
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

    public static void recallC2CMessage(Context ctx) {
        C2CRecallDTO dto = ctx.bodyAsClass(C2CRecallDTO.class);
        if (isBlank(dto.getUserOpenId()) || isBlank(dto.getMessageId())) {
            ctx.json(Result.fail(400, "userOpenId 和 messageId 不能为空"));
            return;
        }
        C2CChat.recallMessage(dto.getUserOpenId(), dto.getMessageId());
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

    public static void createChallenge(Context ctx) {
        if (isBlank(getConfiguredToken())) {
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        String nonce = WebUISessionManager.createChallenge();
        ctx.header("Cache-Control", "no-store");
        ctx.json(Result.success(new ChallengeDTO(
                nonce,
                WebUISessionManager.challengeExpiresAt().toString(),
                "HMAC-SHA256"
        )));
    }

    public static void login(Context ctx) {
        String configuredToken = getConfiguredToken();
        if (isBlank(configuredToken)) {
            clearSessionCookies(ctx);
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        LoginDTO dto = ctx.bodyAsClass(LoginDTO.class);
        if (dto == null || !WebUISessionManager.verifyChallenge(dto.getNonce(), dto.getProof(), configuredToken)) {
            clearSessionCookies(ctx);
            ctx.status(401).json(Result.fail(401, "未授权"));
            return;
        }

        String sessionId = WebUISessionManager.createSession();
        setSessionCookie(ctx, sessionId);
        ctx.json(Result.success("ok"));
    }

    public static void verifyToken(Context ctx) {
        ctx.json(Result.success("ok"));
    }

    public static void logout(Context ctx) {
        String sessionId = ctx.cookie(WebUISessionManager.SESSION_COOKIE);
        WebUISessionManager.removeSession(sessionId);
        clearSessionCookies(ctx);
        ctx.json(Result.success("ok"));
    }

    private static String getConfiguredToken() {
        String token = Config.getInstance().getOfficialWebuiToken();
        if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
            return null;
        }
        return token;
    }

    private static void setSessionCookie(Context ctx, String sessionId) {
        clearLegacyTokenCookie(ctx);
        ctx.res().addHeader("Set-Cookie", WebUISessionManager.SESSION_COOKIE + "=" + sessionId
                + "; Path=/; Max-Age=" + WebUISessionManager.SESSION_TTL_SECONDS
                + "; HttpOnly; SameSite=Strict");
    }

    private static void clearSessionCookies(Context ctx) {
        ctx.res().addHeader("Set-Cookie", WebUISessionManager.SESSION_COOKIE
                + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
        clearLegacyTokenCookie(ctx);
    }

    private static void clearLegacyTokenCookie(Context ctx) {
        ctx.res().addHeader("Set-Cookie", "webui_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
    }

    public record ChallengeDTO(String nonce, String expiresAt, String algorithm) {
    }

    @Data
    public static class LoginDTO {
        private String nonce;
        private String proof;
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
    public static class C2CRecallDTO {
        private String userOpenId;
        private String messageId;
    }

    @Data
    public static class SendGroupMessageDTO {
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

    // ═══════════════ C2C 私聊 ═══════════════

    public static void getGroupFunctions(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(OfficialGroups.getRawFunctionConfig(groupOpenId)));
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

    public static void setGroupFunction(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String functionKey = ctx.pathParam("functionKey");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        OfficialGroups.setFunctionEnabled(groupOpenId, functionKey, enabled, "webui");
        ctx.json(Result.success("ok"));
    }

    public static void listC2CUsers(Context ctx) {
        List<C2CUserDTO> users = new ArrayList<>();
        for (var data : OfficialUsers.listAll()) {
            users.add(toC2CUserDTO(data));
        }
        ctx.json(Result.success(users));
    }

    public static void getC2CUserPermissions(Context ctx) {
        var data = OfficialUsers.getData(ctx.pathParam("userOpenId"));
        ctx.json(Result.success(toC2CUserDTO(data)));
    }

    private static C2CUserDTO toC2CUserDTO(OfficialUsers.UserData data) {
        return new C2CUserDTO(data.userOpenId(), data.role().name(), data.permissions(),
                data.isBlocked(), data.isIgnored());
    }

    public static void setC2CUserRole(Context ctx) {
        var data = OfficialUsers.getData(ctx.pathParam("userOpenId"));
        String role = ctx.queryParam("role");
        try {
            var r = role != null ? PermissionRole.valueOf(role.toUpperCase()) : data.role();
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
        String replyId = dto.getReplyMessageId();
        String messageId;
        try {
            if (replyId != null && !replyId.isBlank()) {
                if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); return; }
                messageId = C2CChat.replyMessage(dto.getUserOpenId(), replyId, dto.getContent());
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); yield null; }
                        yield C2CChat.sendMessage(dto.getUserOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                            ctx.json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                        }
                        yield C2CChat.sendMessage(dto.getUserOpenId(),
                                "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL,
                                dto.getImageValue());
                    }
                    default -> {
                        if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); yield null; }
                        yield C2CChat.sendMessage(dto.getUserOpenId(), dto.getContent());
                    }
                };
            }
        } catch (Exception e) {
            ctx.json(Result.fail(500, "发送失败: " + e.getMessage()));
            return;
        }
        if (messageId == null) return;
        ctx.json(Result.success(new SendGroupMessageResponse(messageId)));
    }

    public record C2CUserDTO(String userOpenId, String role, java.util.Set<String> permissions,
                             boolean isBlocked, boolean isIgnored) {}

    @Data
    public static class SendC2CMessageDTO {
        private String userOpenId;
        private String msgType;
        private String content;
        private String imageType;
        private String imageValue;
        private String replyMessageId;
    }

    // ═══════════════ 反馈管理 ═══════════════

    private static final DateTimeFormatter FEEDBACK_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void listFeedback(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        String filter = ctx.queryParam("filter"); // "unreplied" | "replied" | "all"

        List<FeedbackDTO> list;
        int total;

        if ("replied".equals(filter)) {
            total = FeedbackRepository.countReplied();
            list = FeedbackRepository.findRepliedPaginated(page, pageSize);
        } else if ("all".equals(filter)) {
            total = FeedbackRepository.countAll();
            list = FeedbackRepository.findAllPaginated(page, pageSize);
        } else {
            total = FeedbackRepository.countUnreplied();
            list = FeedbackRepository.findUnrepliedPaginated(page, pageSize);
        }

        List<FeedbackItemDTO> items = list.stream().map(fb -> new FeedbackItemDTO(
                fb.getId(),
                fb.getPlatform(),
                fb.getUserId(),
                fb.getUsername(),
                fb.getGroupId(),
                fb.getSubmitContent(),
                fb.getCreateTime() != null ? fb.getCreateTime().toLocalDateTime().format(FEEDBACK_TIME_FMT) : null,
                fb.isRead(),
                fb.getReplyContent(),
                fb.getReplyTime() != null ? fb.getReplyTime().toLocalDateTime().format(FEEDBACK_TIME_FMT) : null,
                fb.isHidden()
        )).toList();

        ctx.json(Result.success(new FeedbackListResult(items, total, page, pageSize)));
    }

    public static void countFeedback(Context ctx) {
        int unreplied = FeedbackRepository.countUnreplied();
        int replied = FeedbackRepository.countReplied();
        int all = FeedbackRepository.countAll();
        ctx.json(Result.success(new FeedbackCountDTO(unreplied, replied, all)));
    }

    public static void replyFeedback(Context ctx) {
        ReplyFeedbackDTO dto = ctx.bodyAsClass(ReplyFeedbackDTO.class);
        if (isBlank(dto.getId()) || isBlank(dto.getReplyContent())) {
            ctx.json(Result.fail(400, "id 和 replyContent 不能为空"));
            return;
        }
        boolean success = FeedbackRepository.reply(dto.getId(), dto.getReplyContent(), dto.isHidden());
        if (success) {
            ctx.json(Result.success("ok"));
        } else {
            ctx.json(Result.fail(500, "回复失败，可能该反馈不存在"));
        }
    }

    public record FeedbackItemDTO(String id, String platform, String userId, String username,
                                   String groupId, String submitContent, String createTime,
                                   @JsonProperty("isRead") boolean isRead,
                                   String replyContent, String replyTime,
                                   @JsonProperty("isHidden") boolean isHidden) {}

    public record FeedbackListResult(List<FeedbackItemDTO> items, int total, int page, int pageSize) {}

    public record FeedbackCountDTO(int unreplied, int replied, int all) {}

    @Data
    public static class ReplyFeedbackDTO {
        private String id;
        private String replyContent;
        @JsonProperty("isHidden")
        private boolean isHidden;
    }

    // ═══════════════ 用户列表 ═══════════════

    public static void listUserMessages(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        String search = ctx.queryParam("search");
        var result = ChatContentRecord.fetchAllGroupMessages(page, pageSize, search);
        List<UserMessageItemDTO> items = result.records().stream()
                .map(r -> new UserMessageItemDTO(
                        r.unionOpenId(), r.username(), r.groupOpenId(), r.content(),
                        r.memberRole(), r.userRole(), r.eventTimestamp(), r.createdAt()))
                .toList();
        ctx.json(Result.success(new UserMessageListResult(items, result.total(), result.page(), result.pageSize())));
    }

    public record UserMessageItemDTO(String unionOpenId, String username, String groupOpenId,
                                     String content, String memberRole, String userRole,
                                     String eventTimestamp, String createdAt) {}
    public record UserMessageListResult(List<UserMessageItemDTO> items, long total, int page, int pageSize) {}
}
