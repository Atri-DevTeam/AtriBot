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
    public static class SendGroupMessageDTO {
        private String groupOpenId;
        private String msgType;   // "text" | "markdown" | "image"
        private String content;
        private String imageType; // "url" | "base64" (仅 image 时)
        private String imageValue;// 图片 URL 或 base64 (仅 image 时)
    }

    // ═══════════════ C2C 私聊 ═══════════════

    public static void getGroupFunctions(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(GroupList.getRawFunctionConfig(groupOpenId)));
    }

    public static void setGroupWhitelist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        GroupList.setWhitelist(groupOpenId, enabled);
        ctx.json(Result.success("ok"));
    }

    public static void setGroupBlacklist(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        GroupList.setGroupBlacklisted(groupOpenId, enabled);
        ctx.json(Result.success("ok"));
    }

    public static void setGroupAllowedActive(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        GroupList.setAllowedFullMessage(groupOpenId, enabled);
        ctx.json(Result.success("ok"));
    }

    public static void setGroupFunction(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String functionKey = ctx.pathParam("functionKey");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        GroupList.setFunctionEnabled(groupOpenId, functionKey, enabled, "webui");
        ctx.json(Result.success("ok"));
    }

    public static void listC2CUsers(Context ctx) {
        List<C2CUserDTO> users = new ArrayList<>();
        for (var data : C2CList.listAll()) {
            users.add(new C2CUserDTO(data.userOpenId(), data.role().name(), data.permissions()));
        }
        ctx.json(Result.success(users));
    }

    public static void getC2CUserPermissions(Context ctx) {
        var data = C2CList.getData(ctx.pathParam("userOpenId"));
        ctx.json(Result.success(new C2CUserDTO(data.userOpenId(), data.role().name(), data.permissions())));
    }

    public static void setC2CUserRole(Context ctx) {
        var data = C2CList.getData(ctx.pathParam("userOpenId"));
        String role = ctx.queryParam("role");
        try {
            var r = role != null ? top.yzljc.atribot.functions.official.permission.PermissionRole.valueOf(role.toUpperCase()) : data.role();
            C2CList.setPermissionGroup(ctx.pathParam("userOpenId"), r, data.permissions());
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
            C2CList.addPermission(userOpenId, perm);
        } else {
            C2CList.removePermission(userOpenId, perm);
        }
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

    public record C2CUserDTO(String userOpenId, String role, java.util.Set<String> permissions) {}

    @Data
    public static class SendC2CMessageDTO {
        private String userOpenId;
        private String msgType;
        private String content;
        private String imageType;
        private String imageValue;
    }
}
