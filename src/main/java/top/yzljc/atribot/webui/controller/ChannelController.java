package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import top.yzljc.atribot.webui.Result;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelController
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class ChannelController {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Semaphore REQUESTS = new Semaphore(4);

    private ChannelController() {
    }

    public static void query(Context ctx) {
        handle(ctx, false);
    }

    public static void action(Context ctx) {
        handle(ctx, true);
    }

    private static void handle(Context ctx, boolean write) {
        ctx.header("Cache-Control", "no-store");
        ctx.header("Pragma", "no-cache");
        if (write && (!"XMLHttpRequest".equals(ctx.header("X-Requested-With"))
                || ctx.contentType() == null || !ctx.contentType().startsWith("application/json")
                || "cross-site".equals(ctx.header("Sec-Fetch-Site")))) {
            ctx.status(403).json(Result.fail(403, "请通过频道页面提交操作"));
            return;
        }
        if (!REQUESTS.tryAcquire()) {
            ctx.status(429).json(Result.fail(429, "频道请求过多，请稍后重试"));
            return;
        }
        try {
            ObjectNode input = MAPPER.createObjectNode();
            if (write) {
                if (ctx.body().length() > 131072) throw new IllegalArgumentException("请求内容过长");
                JsonNode body;
                try {
                    body = MAPPER.readTree(ctx.body());
                } catch (Exception e) {
                    throw new IllegalArgumentException("JSON 格式错误");
                }
                if (body == null || !body.isObject()) throw new IllegalArgumentException("请求必须是 JSON 对象");
                input = (ObjectNode) body;
                if (!input.path("confirmed").isBoolean() || !input.path("confirmed").booleanValue()) {
                    throw new IllegalArgumentException("操作尚未确认");
                }
            } else {
                for (var entry : ctx.queryParamMap().entrySet()) {
                    if (entry.getValue().size() != 1) throw new IllegalArgumentException("参数不能重复");
                    input.put(entry.getKey(), entry.getValue().getFirst());
                }
            }
            Request request = prepare(ctx.pathParam("operation"), input, write);
            if (write && request.command().equals("do-reply")) {
                request.parameters().put("replier_id", currentReplierId(request.parameters().path("guild_id").asText()));
            }
            ChannelCliResult result = ChannelCalls.client().executeInteractive(request.domain(), request.command(),
                    request.parameters(), write ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
            if (!result.success()) {
                int status = result.timedOut() ? 504 : result.isRateLimited() ? 429 : 502;
                String message = result.isAuthenticationExpired() ? "EMA 登录已过期，请在服务器执行 tencent-channel-cli login"
                        : result.isRateLimited() ? "EMA 请求被限流，请稍后手动刷新"
                        : result.timedOut() ? "EMA 请求超时" : "EMA 操作失败";
                JsonNode error = result.getError();
                if (!result.isAuthenticationExpired() && error.path("message").isTextual()) {
                    String detail = error.path("message").asText();
                    message += "：" + detail.substring(0, Math.min(detail.length(), 500));
                }
                if (write) message += "；未自动重试，请刷新确认实际结果后再操作";
                ctx.status(status).json(Result.fail(status, message));
                return;
            }
            // 只透传业务数据，不暴露 CLI 标准输出、凭据，也不保存 ref 或内容副本。
            ctx.json(Result.success(result.getData().isMissingNode() ? MAPPER.createObjectNode() : result.getData()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Result.fail(400, e.getMessage()));
        } catch (ChannelCliException e) {
            ctx.status(503).json(Result.fail(503, "EMA 暂不可用，请检查频道 CLI 配置及登录状态"));
        } finally {
            REQUESTS.release();
        }
    }

    static Request prepare(String operation, ObjectNode input, boolean write) {
        ObjectNode p = MAPPER.createObjectNode();
        String domain = "feed";
        String command;
        if (!write && operation.equals("guilds")) return new Request("manage", "get-my-join-guild-info", p);
        if (!write && operation.equals("account")) return new Request("manage", "get-user-info", p);
        copy(input, p, "guild_id", true, 32);
        if (!p.path("guild_id").asText().matches("[1-9][0-9]{0,30}")) throw new IllegalArgumentException("频道 ID 格式错误");
        if (!write) {
            switch (operation) {
                case "info", "boards", "members" -> {
                    domain = "manage";
                    command = switch (operation) {
                        case "info" -> "get-guild-info";
                        case "boards" -> "get-guild-channel-list";
                        default -> "get-guild-member-list";
                    };
                    if (operation.equals("members")) copy(input, p, "next_page_token", false, 16384);
                }
                case "feeds" -> {
                    copy(input, p, "channel_id", false, 32);
                    command = p.has("channel_id") ? "get-channel-timeline-feeds" : "get-guild-feeds";
                    p.put("count", 20);
                    copy(input, p, "feed_attach_info", false, 16384);
                }
                case "search" -> {
                    command = "search-guild-feeds";
                    copy(input, p, "query", true, 200);
                    copy(input, p, "next_page_cookie", false, 16384);
                }
                case "detail", "comments", "replies", "share" -> {
                    command = switch (operation) {
                        case "detail" -> "get-feed-detail";
                        case "comments" -> "get-feed-comments";
                        case "replies" -> "get-next-page-replies";
                        default -> "get-feed-share-url";
                    };
                    copy(input, p, "feed_id", true, 256);
                    copy(input, p, "channel_id", !operation.equals("detail"), 32);
                    if (operation.equals("comments") || operation.equals("replies")) {
                        p.put("count", 20);
                        copy(input, p, "attach_info", operation.equals("replies"), 16384);
                    }
                    if (operation.equals("comments")) p.put("reply_list_num", 5);
                    if (operation.equals("replies")) copy(input, p, "comment_id", true, 256);
                }
                default -> throw new IllegalArgumentException("不支持的频道查询");
            }
        } else {
            copy(input, p, "channel_id", true, 32);
            if (!operation.equals("publish")) copy(input, p, "feed_id", true, 256);
            switch (operation) {
                case "publish", "edit" -> {
                    command = operation.equals("publish") ? "publish-feed" : "alter-feed";
                    if (operation.equals("edit")) copy(input, p, "create_time", true, 32);
                    boolean markdown = input.path("markdown").asBoolean(false);
                    copy(input, p, "title", markdown, 300);
                    String content = text(input, "content", true, 60000);
                    p.put(markdown ? "markdown_content" : "content", content);
                    if (markdown || p.has("title")) p.put("feed_type", 2);
                }
                case "delete" -> {
                    command = "del-feed";
                    copy(input, p, "create_time", true, 32);
                }
                case "like", "unlike" -> {
                    command = "do-feed-prefer";
                    p.put("action", operation.equals("like") ? 1 : 3);
                }
                case "pin", "unpin" -> {
                    command = "top-feed";
                    copy(input, p, "create_time", true, 32);
                    copy(input, p, "user_id", true, 32);
                    p.put("action", operation.equals("pin") ? 1 : 2);
                    p.put("top_type", 1);
                }
                case "essence", "unessence" -> {
                    command = "set-feed-essence";
                    p.put("action", operation.equals("essence") ? 1 : 2);
                }
                case "move" -> {
                    command = "move-feed";
                    copy(input, p, "original_channel_id", true, 32);
                }
                case "reply" -> {
                    command = "do-reply";
                    p.put("reply_type", 1);
                    copy(input, p, "feed_author_id", true, 32);
                    copy(input, p, "feed_create_time", true, 32);
                    copy(input, p, "comment_id", true, 256);
                    copy(input, p, "comment_author_id", true, 32);
                    copy(input, p, "comment_create_time", true, 32);
                    copy(input, p, "content", true, 10000);
                    copy(input, p, "target_reply_id", false, 256);
                    if (p.has("target_reply_id")) {
                        copy(input, p, "target_user_id", true, 32);
                        copy(input, p, "target_user_nick", true, 256);
                    } else if (input.hasNonNull("target_user_id") || input.hasNonNull("target_user_nick")) {
                        throw new IllegalArgumentException("回复指定回复时必须提供 target_reply_id");
                    }
                }
                case "comment", "delete-comment", "delete-comment-owner" -> {
                    command = "do-comment";
                    copy(input, p, "feed_create_time", true, 32);
                    if (operation.equals("comment")) {
                        copy(input, p, "content", true, 10000);
                        p.put("comment_type", 1);
                    } else {
                        copy(input, p, "comment_id", true, 256);
                        copy(input, p, "comment_author_id", true, 32);
                        p.put("comment_type", operation.equals("delete-comment") ? 0 : 2);
                    }
                }
                default -> throw new IllegalArgumentException("不支持的频道操作");
            }
        }
        if (p.has("channel_id") && !p.path("channel_id").asText().matches("[1-9][0-9]{0,30}")) {
            throw new IllegalArgumentException("板块 ID 格式错误");
        }
        return new Request(domain, command, p);
    }

    private static String currentReplierId(String guildId) {
        var client = ChannelCalls.client();
        return currentReplierId(guildId, (command, parameters) -> client.executeInteractive(
                "manage", command, parameters, ChannelCliOptions.DEFAULT));
    }

    static String currentReplierId(String guildId, BiFunction<String, ObjectNode, ChannelCliResult> query) {
        JsonNode profile = identityData(query.apply("get-user-info", MAPPER.createObjectNode().put("guild_id", guildId)));
        String nickname = "";
        for (String field : new String[]{"member_name", "nickname", "global_nickname"}) {
            if (profile.path(field).isTextual() && !profile.path(field).asText().isBlank()) {
                nickname = profile.path(field).asText();
                break;
            }
        }
        if (nickname.isEmpty()) throw new IllegalArgumentException("EMA 未返回当前账号的频道昵称；回复尚未发送");

        Set<String> matches = new HashSet<>();
        Set<String> cursors = new HashSet<>();
        String next = "";
        for (int page = 0; page < 5; page++) {
            ObjectNode parameters = MAPPER.createObjectNode().put("guild_id", guildId)
                    .put("keyword", nickname).put("num", 20);
            if (!next.isEmpty()) parameters.put("next_pos", next);
            JsonNode data = identityData(query.apply("guild-member-search", parameters));
            for (JsonNode member : data.path("members")) {
                if (!nickname.equals(member.path("nickname").asText())) continue;
                String id = member.path("tinyid").asText("");
                if (id.matches("[1-9][0-9]{10,30}")) matches.add(id);
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("频道中存在多个与当前账号完全同名的成员，无法唯一确定回复人 ID；回复尚未发送");
            }
            if (data.path("has_more").isBoolean() && !data.path("has_more").asBoolean()) {
                if (matches.size() == 1) return matches.iterator().next();
                throw new IllegalArgumentException("成员搜索未找到与当前账号频道昵称完全匹配的成员；回复尚未发送");
            }
            next = data.path("next_pos").asText("");
            if (next.isBlank() || !cursors.add(next)) break;
        }
        throw new IllegalArgumentException("EMA 成员搜索尚未完成，无法唯一确定回复人 ID；回复尚未发送，请稍后重试");
    }

    private static JsonNode identityData(ChannelCliResult result) {
        if (!result.success()) {
            String reason = result.isAuthenticationExpired() ? "EMA 登录已过期"
                    : result.isRateLimited() ? "EMA 请求被限流"
                    : result.timedOut() ? "EMA 查询超时" : "EMA 账号身份查询失败";
            throw new IllegalArgumentException(reason + "；回复尚未发送，请稍后重试");
        }
        return result.getData();
    }

    private static void copy(ObjectNode input, ObjectNode output, String key, boolean required, int maxLength) {
        String value = text(input, key, required, maxLength);
        if (value != null) output.put(key, value);
    }

    private static String text(ObjectNode input, String key, boolean required, int maxLength) {
        JsonNode node = input.path(key);
        if (node.isMissingNode() || node.isNull() || (node.isTextual() && node.asText().isBlank())) {
            if (required) throw new IllegalArgumentException(key + " 不能为空");
            return null;
        }
        if (!node.isTextual()) throw new IllegalArgumentException(key + " 必须是字符串");
        String value = node.asText();
        if (value.length() > maxLength || value.indexOf('\0') >= 0) throw new IllegalArgumentException(key + " 格式错误或过长");
        return value;
    }

    record Request(String domain, String command, ObjectNode parameters) {
    }
}
