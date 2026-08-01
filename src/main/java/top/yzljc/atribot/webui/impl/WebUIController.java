package top.yzljc.atribot.webui.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import lombok.Data;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.PermissionRole;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.ErrorReportDTO;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.OfficialSendLogDTO;
import top.yzljc.atribot.database.repo.ErrorReportRepository;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.function.official.loot.LootAdminClient;
import top.yzljc.atribot.function.official.imagesource.ImageReviewService;
import top.yzljc.atribot.function.official.imagesource.ImageSourceClient;
import top.yzljc.atribot.function.general.Feedback;
import top.yzljc.atribot.function.napcat.GroupContentRecord;
import top.yzljc.atribot.function.official.ChatContentRecord;
import top.yzljc.atribot.function.official.PushTaskCommand;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.platform.official.OfficialBot;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.repo.PublicOfficialQueryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class WebUIController {

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

    /** 「聊天」页会话列表：群聊 + 私聊合并，各带最后一条消息预览 */
    public static void listChatConversations(Context ctx) {
        int limit = parseInt(ctx.queryParam("limit"), 300);
        ctx.json(Result.success(ChatContentRecord.fetchConversations(limit)));
    }

    /**
     * 置顶是 WebUI 自己的展示偏好，跟消息记录无关，所以单独走一组接口，
     * 不往 ConversationRecord 里塞字段。返回的是会话 key 列表（group:xxx / c2c:xxx）。
     */
    public static void listChatPinned(Context ctx) {
        ctx.json(Result.success(ChatPinnedStore.list()));
    }

    public static void setChatPinned(Context ctx) {
        ChatPinnedDTO dto = ctx.bodyAsClass(ChatPinnedDTO.class);
        if (isBlank(dto.getKey())) {
            ctx.json(Result.fail(400, "key 不能为空"));
            return;
        }
        ChatPinnedStore.setPinned(dto.getKey(), dto.isPinned());
        ctx.json(Result.success(ChatPinnedStore.list()));
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
        ctx.json(Result.success(ChatContentRecord.fetchGroupMessages(groupOpenId, page, pageSize)));
    }

    /** 群成员列表（实为「在本群发过言的人」，官方 API 不提供真实名册） */
    public static void listGroupMembers(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(ChatContentRecord.fetchGroupMembers(groupOpenId)));
    }

    public static void locateGroupMessageByRefIdx(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
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
        var result = ChatContentRecord.locateGroupMessageByReference(
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
                            dto.getRefAuthor(), dto.getRefContent(), dto.getRefAttachments(), refId);
                }
            } else if (dto.getReplyMessageId() != null && !dto.getReplyMessageId().isBlank()) {
                // 被动回复
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    messageId = isBlank(dto.getContent())
                            ? GroupChat.replyMessage(dto.getGroupOpenId(), dto.getReplyMessageId(), type, dto.getImageValue())
                            : GroupChat.replyMessage(dto.getGroupOpenId(), dto.getReplyMessageId(), dto.getContent(), type, dto.getImageValue());
                } else {
                    if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "消息内容不能为空")); return; }
                    messageId = GroupChat.replyMessage(dto.getGroupOpenId(), dto.getReplyMessageId(), dto.getContent());
                }
            } else {
                messageId = switch (msgType) {
                    case "markdown" -> {
                        if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "Markdown 内容不能为空")); yield null; }
                        yield GroupChat.sendMessage(dto.getGroupOpenId(), new Markdown(dto.getContent()));
                    }
                    case "image" -> {
                        if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                            ctx.json(Result.fail(400, "图片类型和内容不能为空")); yield null;
                        }
                        ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                        yield isBlank(dto.getContent())
                                ? GroupChat.sendMessage(dto.getGroupOpenId(), type, dto.getImageValue())
                                : GroupChat.sendMessage(dto.getGroupOpenId(), dto.getContent(), type, dto.getImageValue());
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static void getConfig(Context ctx) {
        ctx.json(Result.success(new ConfigDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId(),
                OfficialBot.BOT_NAME,
                Config.getInstance().getQqApiBaseUrl(),
                Config.getInstance().getDebugGroupOpenId(),
                Config.getInstance().getSuperAdminId()
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

    public record ConfigDTO(String appId, String botOpenId, String botName, String apiBaseUrl,
                            String debugGroupId, String superAdminId) {
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

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
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

    public static void setGroupFunction(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String functionKey = ctx.pathParam("functionKey");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));
        OfficialGroups.setFunctionEnabled(groupOpenId, functionKey, enabled, "webui");
        ctx.json(Result.success("ok"));
    }

    // ═══════════════ Napcat 功能 ═══════════════

    public static void listNapcatGroups(Context ctx) {
        List<NapcatGroupDTO> groups = new ArrayList<>();
        for (String groupId : GroupInformation.fetchAllGroupIds()) {
            groups.add(new NapcatGroupDTO(groupId, GroupInformation.getGroupName(groupId)));
        }
        groups.sort(Comparator.comparing(NapcatGroupDTO::name, Comparator.nullsLast(String::compareTo))
                .thenComparing(NapcatGroupDTO::groupId));
        ctx.json(Result.success(groups));
    }

    public static void getNapcatGroupFeatures(Context ctx) {
        String groupId = ctx.pathParam("groupId");
        if (!GroupInformation.fetchAllGroupIds().contains(groupId)) {
            ctx.json(Result.fail(404, "群聊不在服务范围内"));
            return;
        }

        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String feature : GroupConfigManager.getFeatureList()) {
            features.put(feature, GroupConfigManager.isFeatureEnabled(groupId, feature));
        }
        ctx.json(Result.success(new NapcatFeatureConfigDTO(groupId, features)));
    }

    public static void setNapcatGroupFeature(Context ctx) {
        String groupId = ctx.pathParam("groupId");
        String feature = ctx.pathParam("feature");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));

        if (!GroupConfigManager.getRegisteredFeatures().containsKey(feature)) {
            ctx.json(Result.fail(404, "未知的功能: " + feature));
            return;
        }

        GroupConfigManager.setFeature(groupId, feature, enabled);
        ctx.json(Result.success(new NapcatFeatureDTO(feature, GroupConfigManager.isFeatureEnabled(groupId, feature))));
    }

    public static void fetchNapcatMessages(Context ctx) {
        NapcatMessageRequestDTO dto = ctx.bodyAsClass(NapcatMessageRequestDTO.class);
        if (dto == null || isBlank(dto.getGroupId())) {
            ctx.json(Result.fail(400, "groupId 不能为空"));
            return;
        }
        long groupId;
        try {
            groupId = Long.parseLong(dto.getGroupId());
        } catch (NumberFormatException ignored) {
            ctx.json(Result.fail(400, "groupId 必须是数字"));
            return;
        }
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(dto.getGroupId())) {
            ctx.json(Result.fail(404, "未开启该群的消息监听"));
            return;
        }
        int page = Math.max(dto.getPage(), 1);
        ctx.json(Result.success(GroupContentRecord.fetchMessages(groupId, page)));
    }

    public static void recallNapcatMessages(Context ctx) {
        NapcatRecallDTO dto = ctx.bodyAsClass(NapcatRecallDTO.class);
        if (dto == null || dto.getMessageIds() == null || dto.getMessageIds().isEmpty()) {
            ctx.json(Result.fail(400, "messageIds 不能为空"));
            return;
        }

        for (Long messageId : dto.getMessageIds()) {
            if (messageId != null) {
                GroupMessage.recallMessage(String.valueOf(messageId));
            }
        }
        ctx.json(Result.success("ok"));
    }

    public static void debugOfficialApi(Context ctx) {
        OfficialApiDebugRequestDTO dto = ctx.bodyAsClass(OfficialApiDebugRequestDTO.class);
        if (dto == null || isBlank(dto.getPath())) {
            ctx.json(Result.fail(400, "API 路径不能为空"));
            return;
        }

        String method = normalizeHttpMethod(dto.getMethod());
        if (method == null) {
            ctx.json(Result.fail(400, "不支持的请求方法"));
            return;
        }

        String targetUrl;
        try {
            targetUrl = buildOfficialApiUrl(dto.getPath());
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
            return;
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());

            boolean hasBody = dto.getBody() != null && !dto.getBody().isBlank();
            if (hasBody && !hasDebugHeader(dto.getHeaders(), "content-type")) {
                builder.header("Content-Type", "application/json");
            }

            if (dto.getHeaders() != null) {
                dto.getHeaders().forEach((name, value) -> {
                    if (isAllowedDebugHeader(name) && value != null) {
                        builder.header(name.trim(), value);
                    }
                });
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else if (hasBody) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(dto.getBody()));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = HttpService.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ctx.json(Result.success(new OfficialApiDebugResponseDTO(
                    method,
                    targetUrl,
                    response.statusCode(),
                    response.headers().map(),
                    response.body(),
                    System.currentTimeMillis() - start
            )));
        } catch (Exception e) {
            ctx.json(Result.fail(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static String normalizeHttpMethod(String method) {
        String value = method == null ? "GET" : method.trim().toUpperCase();
        Set<String> allowed = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        return allowed.contains(value) ? value : null;
    }

    private static String buildOfficialApiUrl(String path) {
        String value = path.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            throw new IllegalArgumentException("只允许填写相对 API 路径");
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        String base = Config.getInstance().getQqApiBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + value;
    }

    private static boolean isAllowedDebugHeader(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase();
        return !Set.of("authorization", "host", "content-length", "connection").contains(key);
    }

    private static boolean hasDebugHeader(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return false;
        }
        return headers.keySet().stream().anyMatch(key -> name.equalsIgnoreCase(key));
    }

    public record NapcatGroupDTO(String groupId, String name) {
    }

    public record NapcatFeatureConfigDTO(String groupId, Map<String, Boolean> features) {
    }

    public record NapcatFeatureDTO(String feature, boolean enabled) {
    }

    @Data
    public static class NapcatMessageRequestDTO {
        private String groupId;
        private int page;
    }

    @Data
    public static class NapcatRecallDTO {
        private List<Long> messageIds;
    }

    @Data
    public static class OfficialApiDebugRequestDTO {
        private String method;
        private String path;
        private Map<String, String> headers;
        private String body;
    }

    public record OfficialApiDebugResponseDTO(String method, String url, int statusCode,
                                              Map<String, List<String>> headers, String body,
                                              long durationMillis) {
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
                data.isBlocked(), data.isIgnored(), data.c2cPush());
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
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }

        PermissionRole role = OfficialUsers.getData(userOpenId).role();
        if (!isBlank(dto.getRole())) {
            try {
                role = PermissionRole.valueOf(dto.getRole().trim().toUpperCase());
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

        ctx.json(Result.success(toC2CUserDTO(OfficialUsers.getData(userOpenId))));
    }

    public static void deleteC2CUser(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        if (isBlank(userOpenId)) {
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        if (!OfficialUsers.removeUser(userOpenId)) {
            ctx.json(Result.fail(404, "用户档案不存在或已删除"));
            return;
        }
        ctx.json(Result.success("ok"));
    }

    public static void fetchC2CMessages(Context ctx) {
        String userOpenId = ctx.pathParam("userOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 80);
        ctx.json(Result.success(ChatContentRecord.fetchC2CMessages(userOpenId, page, pageSize)));
    }

    /** 私聊引用来源定位，与 {@link #locateGroupMessageByRefIdx} 同构 */
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
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        String msgType = dto.getMsgType() != null ? dto.getMsgType() : "text";
        String replyId = dto.getReplyMessageId();
        String refId = dto.getRefMessageId();
        String messageId;
        try {
            if (refId != null && !refId.isBlank()) {
                // 引用回复：发送带 message_reference 的主动消息，与群聊同逻辑
                if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); return; }
                messageId = C2CChat.refMessage(dto.getUserOpenId(), refId, dto.getContent());
                if (messageId != null) {
                    ChatContentRecord.patchC2CRefDisplayData(messageId,
                            dto.getRefAuthor(), dto.getRefContent(), dto.getRefAttachments(), refId);
                }
            } else if (replyId != null && !replyId.isBlank()) {
                if ("image".equals(msgType)) {
                    if (isBlank(dto.getImageType()) || isBlank(dto.getImageValue())) {
                        ctx.json(Result.fail(400, "图片类型和内容不能为空")); return;
                    }
                    ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                    messageId = isBlank(dto.getContent())
                            ? C2CChat.replyMessage(dto.getUserOpenId(), replyId, type, dto.getImageValue())
                            : C2CChat.replyMessage(dto.getUserOpenId(), replyId, dto.getContent(), type, dto.getImageValue());
                } else {
                    if (isBlank(dto.getContent())) { ctx.json(Result.fail(400, "内容不能为空")); return; }
                    messageId = C2CChat.replyMessage(dto.getUserOpenId(), replyId, dto.getContent());
                }
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
                        ImageType type = "base64".equalsIgnoreCase(dto.getImageType()) ? ImageType.BASE64 : ImageType.URL;
                        yield isBlank(dto.getContent())
                                ? C2CChat.sendMessage(dto.getUserOpenId(), type, dto.getImageValue())
                                : C2CChat.sendMessage(dto.getUserOpenId(), dto.getContent(), type, dto.getImageValue());
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
        if (messageId == null) {
            ctx.json(Result.fail(500, "发送失败：未返回消息ID"));
            return;
        }
        ctx.json(Result.success(new SendGroupMessageResponse(messageId)));
    }

    public static void sendC2CStreamMessage(Context ctx) {
        SendC2CStreamDTO dto = ctx.bodyAsClass(SendC2CStreamDTO.class);
        if (isBlank(dto.getUserOpenId())) {
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        if (isBlank(dto.getContent())) {
            ctx.json(Result.fail(400, "内容不能为空"));
            return;
        }
        List<Markdown> deltas = java.util.Arrays.stream(dto.getContent().split("\n"))
                .filter(s -> !s.isBlank())
                .map(Markdown::new)
                .toList();
        if (deltas.isEmpty()) {
            ctx.json(Result.fail(400, "内容不能为空"));
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
        } catch (Exception e) {
            ctx.json(Result.fail(500, "发送失败: " + e.getMessage()));
            return;
        }
        if (messageId == null) {
            ctx.json(Result.fail(500, "发送失败：未返回消息ID"));
            return;
        }
        ctx.json(Result.success(new SendGroupMessageResponse(messageId)));
    }

    @Data
    public static class SendC2CStreamDTO {
        private String userOpenId;
        private String content;
        private String replyMessageId;
    }

    public record C2CUserDTO(String userOpenId, String role, java.util.Set<String> permissions,
                             boolean isBlocked, boolean isIgnored, boolean c2cPush) {}

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
            // 主动推送涉及网络，别阻塞 WebUI 请求线程
            ThreadManager.execute(() -> Feedback.dispatchReply(dto.getId()));
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

    public static void listErrorReports(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String keyword = ctx.queryParam("keyword");
        String exceptionType = ctx.queryParam("exceptionType");

        int total = ErrorReportRepository.count(keyword, exceptionType);
        List<ErrorItemDTO> items = ErrorReportRepository.findPaginated(page, pageSize, keyword, exceptionType)
                .stream()
                .map(WebUIController::toErrorItem)
                .toList();

        ctx.json(Result.success(new ErrorListResult(items, total, page, pageSize)));
    }

    /**
     * 按 traceId 查询单条错误详情，含完整堆栈
     */
    public static void getErrorReport(Context ctx) {
        String traceId = ctx.pathParam("traceId");
        if (isBlank(traceId)) {
            ctx.json(Result.fail(400, "traceId 不能为空"));
            return;
        }

        ErrorReportDTO report = ErrorReportRepository.findByTraceId(traceId.trim());
        if (report == null) {
            ctx.json(Result.fail(404, "未找到该 traceId 对应的错误报告"));
            return;
        }

        ctx.json(Result.success(new ErrorDetailDTO(
                report.getTraceId(),
                report.getClassName(),
                report.getExceptionType(),
                report.getExceptionMessage(),
                report.getStackTrace() != null ? report.getStackTrace() : List.of(),
                report.getCauseType(),
                report.getCauseMessage(),
                report.getCauseStackTrace() != null ? report.getCauseStackTrace() : List.of(),
                formatFeedbackTime(report.getCreateTime())
        )));
    }

    public static void errorReportStats(Context ctx) {
        ctx.json(Result.success(new ErrorStatsDTO(
                ErrorReportRepository.count(null, null),
                ErrorReportRepository.countSince(24),
                ErrorReportRepository.countSince(24 * 7),
                ErrorReportRepository.topExceptionTypes(8)
        )));
    }

    private static ErrorItemDTO toErrorItem(ErrorReportDTO report) {
        return new ErrorItemDTO(
                report.getTraceId(),
                report.getClassName(),
                report.getExceptionType(),
                report.getExceptionMessage(),
                report.getCauseType(),
                report.getCauseMessage(),
                formatFeedbackTime(report.getCreateTime())
        );
    }

    private static String formatFeedbackTime(java.sql.Timestamp ts) {
        return ts != null ? ts.toLocalDateTime().format(FEEDBACK_TIME_FMT) : null;
    }

    public record ErrorItemDTO(String traceId, String className, String exceptionType, String exceptionMessage,
                               String causeType, String causeMessage, String createTime) {}

    public record ErrorDetailDTO(String traceId, String className, String exceptionType, String exceptionMessage,
                                 List<String> stackTrace, String causeType, String causeMessage,
                                 List<String> causeStackTrace, String createTime) {}

    public record ErrorListResult(List<ErrorItemDTO> items, int total, int page, int pageSize) {}

    public record ErrorStatsDTO(int total, int last24h, int last7d, Map<String, Integer> topExceptionTypes) {}

    // ═══════════════ 发送日志 ═══════════════

    public static void listOfficialSendLogs(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String type = normalizeSendLogType(ctx.queryParam("type"));
        String keyword = ctx.queryParam("keyword");

        int total = OfficialSendLogRepository.count(type, keyword);
        List<SendLogItemDTO> items = OfficialSendLogRepository.findPaginated(page, pageSize, type, keyword)
                .stream()
                .map(WebUIController::toSendLogItem)
                .toList();
        ctx.json(Result.success(new SendLogListResult(items, total, page, pageSize)));
    }

    public static void getOfficialSendLog(Context ctx) {
        long id = parseLong(ctx.pathParam("id"), -1L);
        if (id <= 0) {
            ctx.json(Result.fail(400, "日志 id 无效"));
            return;
        }
        OfficialSendLogDTO log = OfficialSendLogRepository.findById(id);
        if (log == null) {
            ctx.json(Result.fail(404, "未找到该发送日志"));
            return;
        }
        ctx.json(Result.success(toSendLogDetail(log)));
    }

    public static void officialSendLogStats(Context ctx) {
        var stats = OfficialSendLogRepository.stats();
        ctx.json(Result.success(new SendLogStatsDTO(stats.all(), stats.send(), stats.response(), stats.error())));
    }

    private static String normalizeSendLogType(String raw) {
        if (isBlank(raw) || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        String type = raw.trim().toUpperCase();
        return switch (type) {
            case OfficialSendLogRepository.TYPE_SEND,
                 OfficialSendLogRepository.TYPE_RESPONSE,
                 OfficialSendLogRepository.TYPE_ERROR -> type;
            default -> null;
        };
    }

    private static SendLogItemDTO toSendLogItem(OfficialSendLogDTO log) {
        return new SendLogItemDTO(
                log.getId(),
                log.getTraceId(),
                log.getEntryType(),
                log.getScene(),
                log.getMethod(),
                log.getUrl(),
                log.getRequestJson(),
                log.getResponseStatus(),
                log.getResponseBody(),
                log.getErrorCode(),
                log.getErrorReason(),
                log.getErrorMessage(),
                formatFeedbackTime(log.getCreateTime())
        );
    }

    private static SendLogDetailDTO toSendLogDetail(OfficialSendLogDTO log) {
        return new SendLogDetailDTO(
                log.getId(),
                log.getTraceId(),
                log.getEntryType(),
                log.getScene(),
                log.getMethod(),
                log.getUrl(),
                log.getRequestJson(),
                log.getResponseStatus(),
                log.getResponseBody(),
                log.getErrorCode(),
                log.getErrorReason(),
                log.getErrorMessage(),
                formatFeedbackTime(log.getCreateTime())
        );
    }

    public record SendLogItemDTO(long id, String traceId, String entryType, String scene, String method, String url,
                                 String requestJson, Integer responseStatus, String responseBody,
                                 Integer errorCode, String errorReason, String errorMessage,
                                 String createTime) {}

    public record SendLogDetailDTO(long id, String traceId, String entryType, String scene, String method, String url,
                                   String requestJson, Integer responseStatus, String responseBody,
                                   Integer errorCode, String errorReason, String errorMessage,
                                   String createTime) {}

    public record SendLogListResult(List<SendLogItemDTO> items, int total, int page, int pageSize) {}

    public record SendLogStatsDTO(int all, int send, int response, int error) {}

    // ═══════════════ 用户列表 ═══════════════

    public static void listUserMessages(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        String search = ctx.queryParam("search");
        var result = ChatContentRecord.fetchAllGroupMessages(page, pageSize, search);
        List<UserMessageItemDTO> items = result.records().stream()
                .map(r -> new UserMessageItemDTO(
                        r.unionOpenId(), r.username(), r.groupOpenId(), r.content(),
                        r.memberRole(), r.userRole(), r.messageType(), r.attachments(),
                        r.ark(), r.mentions(), r.eventTimestamp(), r.createdAt()))
                .toList();
        ctx.json(Result.success(new UserMessageListResult(items, result.total(), result.page(), result.pageSize())));
    }

    public record UserMessageItemDTO(String unionOpenId, String username, String groupOpenId,
                                     String content, String memberRole, String userRole,
                                     Integer messageType, String attachments, String ark, String mentions,
                                     String eventTimestamp, String createdAt) {}
    public record UserMessageListResult(List<UserMessageItemDTO> items, long total, int page, int pageSize) {}

    public static void listUserC2CMessages(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        String search = ctx.queryParam("search");
        var result = ChatContentRecord.fetchAllC2CMessages(page, pageSize, search);
        List<UserC2CMessageItemDTO> items = result.records().stream()
                .map(r -> new UserC2CMessageItemDTO(
                        r.unionOpenId(), r.username(), r.content(), r.userRole(),
                        r.source(), r.messageType(), r.eventTimestamp(), r.attachments(), r.ark(), r.createdAt()))
                .toList();
        ctx.json(Result.success(new UserC2CMessageListResult(items, result.total(), result.page(), result.pageSize())));
    }

    public record UserC2CMessageItemDTO(String unionOpenId, String username, String content,
                                        String userRole, String source, Integer messageType,
                                        String eventTimestamp, String attachments, String ark, String createdAt) {}
    public record UserC2CMessageListResult(List<UserC2CMessageItemDTO> items, long total, int page, int pageSize) {}

    // ═══════════════ 公开官方机器人查询 ═══════════════

    private static final DateTimeFormatter PUBLIC_QUERY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long PUBLIC_QUERY_CACHE_TTL_MILLIS = 60_000L;
    private static final ConcurrentHashMap<String, PublicQueryCacheEntry> PUBLIC_QUERY_CACHE = new ConcurrentHashMap<>();

    public static void publicOfficialGroupReceivedMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String cacheKey = publicCacheKey("group_received", window, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_group_received_messages",
                "group",
                window.startString(),
                window.endString(),
                groupOpenId,
                null,
                PublicOfficialQueryRepository.countGroupMessages(false, window.start(), window.end(), groupOpenId)
        ));
    }

    public static void publicOfficialGroupSentMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String cacheKey = publicCacheKey("group_sent", window, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_group_sent_messages",
                "group",
                window.startString(),
                window.endString(),
                groupOpenId,
                null,
                PublicOfficialQueryRepository.countGroupMessages(true, window.start(), window.end(), groupOpenId)
        ));
    }

    public static void publicOfficialC2CReceivedMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("c2c_received", window, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_c2c_received_messages",
                "c2c",
                window.startString(),
                window.endString(),
                null,
                userOpenId,
                PublicOfficialQueryRepository.countC2CMessages(false, window.start(), window.end(), userOpenId)
        ));
    }

    public static void publicOfficialC2CSentMessages(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("c2c_sent", window, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> new PublicMessageCountDTO(
                "official_c2c_sent_messages",
                "c2c",
                window.startString(),
                window.endString(),
                null,
                userOpenId,
                PublicOfficialQueryRepository.countC2CMessages(true, window.start(), window.end(), userOpenId)
        ));
    }

    public static void publicOfficialDau(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String groupOpenId = trimToNull(ctx.queryParam("groupOpenId"));
        String userOpenId = trimToNull(firstNonBlank(ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        String cacheKey = publicCacheKey("dau", window, groupOpenId, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> {
            var stats = PublicOfficialQueryRepository.queryDau(window.start(), window.end(), groupOpenId, userOpenId);
            double totalDau = PublicOfficialQueryRepository.queryAverageDailyDau();
            long groupReceiveMessages = PublicOfficialQueryRepository.countGroupMessages(false, window.start(), window.end(), groupOpenId);
            long groupSendMessages = PublicOfficialQueryRepository.countGroupMessages(true, window.start(), window.end(), groupOpenId);
            long c2cReceiveMessages = PublicOfficialQueryRepository.countC2CMessages(false, window.start(), window.end(), userOpenId);
            long c2cSendMessages = PublicOfficialQueryRepository.countC2CMessages(true, window.start(), window.end(), userOpenId);
            return new PublicDauDTO(
                    window.startString(),
                    window.endString(),
                    groupOpenId,
                    userOpenId,
                    stats.totalReceiveUsers(),
                    totalDau,
                    groupReceiveMessages,
                    groupSendMessages,
                    c2cReceiveMessages,
                    c2cSendMessages
            );
        });
    }

    /**
     * 按日聚合的消息量与 DAU 序列，供统计页画折线图。
     * 单点接口只能给出区间总量，画不出趋势。
     */
    public static void publicOfficialSeries(Context ctx) {
        QueryWindow window = parseQueryWindowOrFail(ctx);
        if (window == null) return;
        String cacheKey = publicCacheKey("series", window, null, null);
        publicAsyncCached(ctx, cacheKey, () -> new PublicSeriesDTO(
                window.startString(),
                window.endString(),
                PublicOfficialQueryRepository.queryDailySeries(window.start(), window.end())
        ));
    }

    public record PublicSeriesDTO(String startTime, String endTime,
                                  List<PublicOfficialQueryRepository.DailyPoint> points) {}

    public static void publicOfficialUserInfo(Context ctx) {
        String userOpenId = trimToNull(firstNonBlank(ctx.pathParam("userOpenId"), ctx.queryParam("userOpenId"), ctx.queryParam("unionOpenId")));
        if (userOpenId == null) {
            ctx.json(Result.fail(400, "userOpenId 不能为空"));
            return;
        }
        String cacheKey = publicCacheKey("user_info", null, null, userOpenId);
        publicAsyncCached(ctx, cacheKey, () -> {
            var user = OfficialUsers.getData(userOpenId);
            var stats = PublicOfficialQueryRepository.queryUserMessageStats(userOpenId);
            return new PublicUserInfoDTO(
                    user.userOpenId(),
                    user.role().name(),
                    user.permissions(),
                    user.isBlocked(),
                    user.isIgnored(),
                    user.c2cPush(),
                    stats.c2cReceivedMessages(),
                    stats.c2cSentMessages(),
                    stats.groupReceivedMessages(),
                    stats.firstSeenAt(),
                    stats.lastSeenAt(),
                    stats.lastUsername()
            );
        });
    }

    public static void publicOfficialGroupInfo(Context ctx) {
        String groupOpenId = trimToNull(firstNonBlank(ctx.pathParam("groupOpenId"), ctx.queryParam("groupOpenId")));
        if (groupOpenId == null) {
            ctx.json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }
        String cacheKey = publicCacheKey("group_info", null, groupOpenId, null);
        publicAsyncCached(ctx, cacheKey, () -> {
            var group = OfficialGroups.getData(groupOpenId);
            var stats = PublicOfficialQueryRepository.queryGroupMessageStats(groupOpenId);
            return new PublicGroupInfoDTO(
                    group.groupOpenId(),
                    group.opMemberOpenId(),
                    group.timestamp(),
                    group.isWhitelist(),
                    group.isBlacklisted(),
                    group.isAllowedActive(),
                    group.realGroupId(),
                    stats.receivedMessages(),
                    stats.sentMessages(),
                    stats.activeUsers(),
                    stats.firstSeenAt(),
                    stats.lastSeenAt()
            );
        });
    }

    private static <T> void publicAsyncCached(Context ctx, String cacheKey, Supplier<T> supplier) {
        PublicQueryCacheEntry cached = PUBLIC_QUERY_CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAt() < PUBLIC_QUERY_CACHE_TTL_MILLIS) {
            ctx.json(cached.result());
            return;
        }

        publicAsync(ctx, () -> {
            T data = supplier.get();
            Result<T> result = Result.success(data);
            PUBLIC_QUERY_CACHE.put(cacheKey, new PublicQueryCacheEntry(now, result));
            cleanupPublicQueryCache(now);
            return result;
        }, true);
    }

    private static <T> void publicAsync(Context ctx, Supplier<T> supplier) {
        publicAsync(ctx, supplier, false);
    }

    private static <T> void publicAsync(Context ctx, Supplier<T> supplier, boolean alreadyWrapped) {
        ctx.future(() -> ThreadManager.supplyAsync(() -> {
            try {
                return alreadyWrapped ? supplier.get() : Result.success(supplier.get());
            } catch (IllegalArgumentException e) {
                return Result.fail(400, e.getMessage());
            } catch (Exception e) {
                return Result.fail(500, "公开查询失败: " + e.getMessage());
            }
        }).thenAccept(ctx::json));
    }

    private static String publicCacheKey(String name, QueryWindow window, String groupOpenId, String userOpenId) {
        String start = window == null ? "-" : String.valueOf(window.startString());
        String end = window == null ? "-" : String.valueOf(window.endString());
        return name + "|start=" + start + "|end=" + end + "|group=" + nullToDash(groupOpenId) + "|user=" + nullToDash(userOpenId);
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private static void cleanupPublicQueryCache(long now) {
        if (PUBLIC_QUERY_CACHE.size() < 512) {
            return;
        }
        PUBLIC_QUERY_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAt() >= PUBLIC_QUERY_CACHE_TTL_MILLIS);
    }

    private static QueryWindow parseQueryWindowOrFail(Context ctx) {
        try {
            return parseQueryWindow(ctx);
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
            return null;
        }
    }

    private static QueryWindow parseQueryWindow(Context ctx) {
        if (Boolean.parseBoolean(ctx.queryParam("all"))) {
            return new QueryWindow(null, null);
        }

        String startValue = firstNonBlank(ctx.queryParam("start"), ctx.queryParam("startTime"), ctx.queryParam("from"));
        String endValue = firstNonBlank(ctx.queryParam("end"), ctx.queryParam("endTime"), ctx.queryParam("to"));
        LocalDateTime start;
        LocalDateTime end;

        if (isBlank(startValue) && isBlank(endValue)) {
            start = LocalDate.now(BEIJING_ZONE).atStartOfDay();
            end = start.plusDays(1);
        } else if (isBlank(startValue)) {
            end = parsePublicQueryTime(endValue, true);
            start = end.minusDays(1);
        } else if (isBlank(endValue)) {
            start = parsePublicQueryTime(startValue, false);
            end = start.plusDays(1);
        } else {
            start = parsePublicQueryTime(startValue, false);
            end = parsePublicQueryTime(endValue, true);
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end 必须晚于 start");
        }
        return new QueryWindow(start, end);
    }

    private static LocalDateTime parsePublicQueryTime(String value, boolean endOfDate) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("时间参数不能为空");
        }

        String normalized = value.trim();
        try {
            if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(normalized);
                return endOfDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
            }
            if (normalized.indexOf(' ') > 0) {
                return LocalDateTime.parse(normalized, PUBLIC_QUERY_TIME_FMT);
            }
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式无效，支持 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss 或 ISO LocalDateTime");
        }
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record QueryWindow(LocalDateTime start, LocalDateTime end) {
        String startString() {
            return start == null ? null : start.format(PUBLIC_QUERY_TIME_FMT);
        }

        String endString() {
            return end == null ? null : end.format(PUBLIC_QUERY_TIME_FMT);
        }
    }

    private record PublicQueryCacheEntry(long createdAt, Result<?> result) {
    }

    public record PublicMessageCountDTO(String metric, String scope, String startTime, String endTime,
                                        String groupOpenId, String userOpenId, long count) {
    }

    public record PublicDauDTO(String startTime, String endTime, String groupOpenId, String userOpenId,
                               long dau, double totalDau,
                               long groupReceiveMessages, long groupSendMessages,
                               long c2cReceiveMessages, long c2cSendMessages) {
    }

    public record PublicUserInfoDTO(String userOpenId, String role, java.util.Set<String> permissions,
                                    boolean isBlocked, boolean isIgnored, boolean c2cPush,
                                    long c2cReceivedMessages, long c2cSentMessages,
                                    long groupReceivedMessages, String firstSeenAt,
                                    String lastSeenAt, String lastUsername) {
    }

    public record PublicGroupInfoDTO(String groupOpenId, String opMemberOpenId, long timestamp,
                                     boolean whitelist, boolean blacklisted, boolean allowedActive,
                                     Long realGroupId, long receivedMessages, long sentMessages,
                                     long activeUsers, String firstSeenAt, String lastSeenAt) {
    }

    // ═══════════════ 图源管理 ═══════════════

    private static final DateTimeFormatter GALLERY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void listGallery(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 24));
        String status = ctx.queryParam("status"); // PENDING | REVIEWED | DENIED | all

        String filter = ImageReviewStatus.isValid(status) ? status.toUpperCase() : null;
        int total = ImageSourceRepository.countByStatus(filter);
        List<ImageSourceDTO> list = ImageSourceRepository.findPaginated(filter, page, pageSize);

        List<GalleryItemDTO> items = list.stream().map(WebUIController::toGalleryItem).toList();
        ctx.json(Result.success(new GalleryListResult(items, total, page, pageSize)));
    }

    public static void countGallery(Context ctx) {
        int pending = ImageSourceRepository.countByStatus(ImageReviewStatus.PENDING.name());
        int reviewed = ImageSourceRepository.countByStatus(ImageReviewStatus.REVIEWED.name());
        int denied = ImageSourceRepository.countByStatus(ImageReviewStatus.DENIED.name());
        int all = ImageSourceRepository.countByStatus(null);
        ctx.json(Result.success(new GalleryCountDTO(pending, reviewed, denied, all)));
    }

    public static void reviewGallery(Context ctx) {
        ReviewGalleryDTO dto = ctx.bodyAsClass(ReviewGalleryDTO.class);
        if (isBlank(dto.getId()) || !ImageReviewStatus.isValid(dto.getStatus())) {
            ctx.json(Result.fail(400, "id 与合法的 status 不能为空"));
            return;
        }
        boolean success = ImageReviewService.review(dto.getId(), ImageReviewStatus.of(dto.getStatus()),
                REVIEWER_NAME, dto.getRemark());
        ctx.json(success ? Result.success("ok") : Result.fail(500, "审核失败，可能该投稿不存在"));
    }

    public static void reviewGalleryBatch(Context ctx) {
        ReviewGalleryBatchDTO dto = ctx.bodyAsClass(ReviewGalleryBatchDTO.class);
        if (dto.getIds() == null || dto.getIds().isEmpty() || !ImageReviewStatus.isValid(dto.getStatus())) {
            ctx.json(Result.fail(400, "ids 与合法的 status 不能为空"));
            return;
        }
        ImageReviewStatus status = ImageReviewStatus.of(dto.getStatus());
        int ok = 0;
        for (String id : dto.getIds()) {
            if (isBlank(id)) continue;
            if (ImageReviewService.review(id, status, REVIEWER_NAME, dto.getRemark())) ok++;
        }
        ctx.json(Result.success(new GalleryBatchResult(ok, dto.getIds().size())));
    }

    public static void deleteGallery(Context ctx) {
        DeleteGalleryDTO dto = ctx.bodyAsClass(DeleteGalleryDTO.class);
        if (isBlank(dto.getId())) {
            ctx.json(Result.fail(400, "id 不能为空"));
            return;
        }
        ImageSourceDTO image = ImageSourceRepository.findById(dto.getId());
        if (image == null) {
            ctx.json(Result.fail(404, "投稿记录不存在"));
            return;
        }
        ImageSourceClient.RemoteResult remoteResult = ImageSourceClient.delete(image);
        if (!remoteResult.ok()) {
            ctx.json(Result.fail(500, "远端删除失败：" + remoteResult.message()));
            return;
        }
        boolean success = ImageSourceRepository.delete(dto.getId());
        ctx.json(success ? Result.success("ok") : Result.fail(500, "删除失败，可能该投稿不存在"));
    }

    private static GalleryItemDTO toGalleryItem(ImageSourceDTO dto) {
        return new GalleryItemDTO(
                dto.getId(), dto.getImageUuid(), dto.getPlatform(), dto.getUploaderId(), dto.getUploaderName(),
                dto.getGroupId(), ImageSourceClient.viewUrl(dto), dto.getFileName(), dto.getContentType(),
                dto.getWidth(), dto.getHeight(), dto.getFileSize(),
                dto.getProcessedWidth(), dto.getProcessedHeight(), dto.getProcessedFileSize(),
                dto.getHash(), dto.getReviewStatus(),
                dto.getReviewer(), dto.getReviewRemark(),
                dto.getReviewTime() != null ? dto.getReviewTime().toLocalDateTime().format(GALLERY_TIME_FMT) : null,
                dto.getCreateTime() != null ? dto.getCreateTime().toLocalDateTime().format(GALLERY_TIME_FMT) : null,
                dto.isNotified()
        );
    }

    /** WebUI 目前只有单一管理员会话，没有独立账号体系，审核人统一记为 webui */
    private static final String REVIEWER_NAME = "webui";

    public record GalleryItemDTO(String id, String imageUuid, String platform, String uploaderId,
                                 String uploaderName, String groupId, String displayUrl, String fileName,
                                 String contentType, int width, int height, long fileSize,
                                 int processedWidth, int processedHeight, long processedFileSize, String hash,
                                 String reviewStatus, String reviewer, String reviewRemark,
                                 String reviewTime, String createTime,
                                 @JsonProperty("isNotified") boolean isNotified) {
    }

    public record GalleryListResult(List<GalleryItemDTO> items, int total, int page, int pageSize) {
    }

    public record GalleryCountDTO(int pending, int reviewed, int denied, int all) {
    }

    public record GalleryBatchResult(int success, int total) {
    }

    @Data
    public static class ReviewGalleryDTO {
        private String id;
        private String status;
        private String remark;
    }

    @Data
    public static class ReviewGalleryBatchDTO {
        private List<String> ids;
        private String status;
        private String remark;
    }

    @Data
    public static class DeleteGalleryDTO {
        private String id;
    }

    // ==================== 抽卡系统管理 ====================

    public static void listLootItems(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        JsonNode resp = LootAdminClient.listItems(page, pageSize);
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "抽卡目录服务暂不可用"));
            return;
        }
        JsonNode data = resp.path("data");
        if (data instanceof ObjectNode objectNode) {
            objectNode.put("imageBaseUrl", ResourcesProperties.LOOTS_ITEM_IMAGE_API);
        }
        ctx.json(Result.success(data));
    }

    public static void createLootItem(Context ctx) {
        String displayName = ctx.formParam("displayName");
        String description = ctx.formParam("description");
        UploadedFile file = ctx.uploadedFile("image");
        if (isBlank(displayName) || file == null) {
            ctx.json(Result.fail(400, "displayName 与 image 不能为空"));
            return;
        }

        byte[] bytes;
        try (InputStream is = file.content()) {
            bytes = is.readAllBytes();
        } catch (IOException e) {
            ctx.json(Result.fail(400, "读取上传图片失败"));
            return;
        }

        JsonNode resp = LootAdminClient.createItem(displayName, description, bytes, file.filename(), file.contentType());
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "创建物品卡失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void updateLootItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        UpdateLootItemDTO dto = ctx.bodyAsClass(UpdateLootItemDTO.class);
        JsonNode resp = LootAdminClient.updateItem(itemId, dto.getDisplayName(), dto.getDescription());
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "更新物品卡失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void replaceLootItemImage(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        UploadedFile file = ctx.uploadedFile("image");
        if (file == null) {
            ctx.json(Result.fail(400, "image 不能为空"));
            return;
        }

        byte[] bytes;
        try (InputStream is = file.content()) {
            bytes = is.readAllBytes();
        } catch (IOException e) {
            ctx.json(Result.fail(400, "读取上传图片失败"));
            return;
        }

        JsonNode resp = LootAdminClient.replaceItemImage(itemId, bytes, file.filename(), file.contentType());
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "更换物品卡图片失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void deleteLootItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        JsonNode resp = LootAdminClient.deleteItem(itemId);
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "删除物品卡失败"));
            return;
        }
        ctx.json(Result.success("ok"));
    }

    public static void listCoinLeaderboard(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        List<LootRepository.CoinLeaderboardEntry> entries = LootRepository.getCoinLeaderboard(page, pageSize);
        int total = LootRepository.countUsers();
        ctx.json(Result.success(new CoinLeaderboardResult(entries, total, page, pageSize)));
    }

    public static void adjustUserCoins(Context ctx) {
        String userId = ctx.pathParam("userId");
        AdjustCoinsDTO dto = ctx.bodyAsClass(AdjustCoinsDTO.class);
        if (isBlank(dto.getOp()) || dto.getAmount() == null) {
            ctx.json(Result.fail(400, "op 与 amount 不能为空"));
            return;
        }

        boolean success = switch (dto.getOp()) {
            case "set" -> LootRepository.setCoins(userId, dto.getAmount());
            case "add" -> LootRepository.addCoins(userId, dto.getAmount());
            case "remove" -> LootRepository.removeCoins(userId, dto.getAmount());
            default -> false;
        };

        ctx.json(success ? Result.success(LootRepository.getCoins(userId)) : Result.fail(400, "操作失败，请检查 op 参数或余额"));
    }

    public static void listLootUsers(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        String search = ctx.queryParam("search");

        List<LootRepository.UserLootsSummary> summaries = LootRepository.listUsers(search, page, pageSize);
        int total = LootRepository.countUsersMatching(search);

        List<LootUserListItemDTO> items = summaries.stream()
                .map(s -> new LootUserListItemDTO(s.userId(), s.coins(), s.totalLootCount()))
                .toList();
        ctx.json(Result.success(new LootUserListResult(items, total, page, pageSize)));
    }

    public static void getUserLootsDetail(Context ctx) {
        String userId = ctx.pathParam("userId");
        LootRepository.UserLootsSummary summary = LootRepository.getUserSummary(userId);
        ctx.json(Result.success(new UserLootsDetailDTO(summary.userId(), summary.coins(), summary.loots())));
    }

    public static void grantUserLoot(Context ctx) {
        String userId = ctx.pathParam("userId");
        GrantLootDTO dto = ctx.bodyAsClass(GrantLootDTO.class);
        if (isBlank(dto.getItemId()) || isBlank(dto.getDisplayName())) {
            ctx.json(Result.fail(400, "itemId 与 displayName 不能为空"));
            return;
        }
        String way = isBlank(dto.getWay()) ? "管理员赠与" : dto.getWay();
        LootRepository.LootRecord record = LootRepository.appendLoot(userId, dto.getItemId(), dto.getDisplayName(), way);
        ctx.json(record != null ? Result.success(record) : Result.fail(500, "赠送物品失败"));
    }

    public static void revokeUserLoot(Context ctx) {
        String userId = ctx.pathParam("userId");
        String itemId = ctx.pathParam("itemId");
        boolean success = LootRepository.adminRemoveLoot(userId, itemId);
        ctx.json(success ? Result.success("ok") : Result.fail(404, "该用户未持有此物品卡"));
    }

    public record CoinLeaderboardResult(List<LootRepository.CoinLeaderboardEntry> items, int total, int page, int pageSize) {
    }

    public record LootUserListItemDTO(String userId, int coins, int cardCount) {
    }

    public record LootUserListResult(List<LootUserListItemDTO> items, int total, int page, int pageSize) {
    }

    public record UserLootsDetailDTO(String userId, int coins, List<LootRepository.LootRecord> loots) {
    }

    @Data
    public static class UpdateLootItemDTO {
        private String displayName;
        private String description;
    }

    @Data
    public static class AdjustCoinsDTO {
        private String op;
        private Integer amount;
    }

    @Data
    public static class GrantLootDTO {
        private String itemId;
        private String displayName;
        private String way;
    }
}
