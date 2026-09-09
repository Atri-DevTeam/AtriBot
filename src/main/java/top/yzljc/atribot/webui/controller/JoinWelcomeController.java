package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.GroupRepository;
import top.yzljc.atribot.function.tasks.GroupJoinWelcome;
import top.yzljc.atribot.function.impl.JoinWelcomeDAO;
import top.yzljc.atribot.webui.Result;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author YZ_Ljc_
 * @ClassName JoinWelcomeController
 * @Created_at 2026/09/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
@Slf4j
public final class JoinWelcomeController {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong NEXT_TEST_AT = new AtomicLong();

    public static void get(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        try {
            String json = GroupRepository.getJoinWelcomeConfigJsonOrThrow(groupId);
            JsonNode config = json == null ? MAPPER.createObjectNode() : MAPPER.readTree(json);
            if (config == null || !config.isObject()) throw new IllegalStateException("欢迎配置不是对象");
            ObjectNode response = MAPPER.createObjectNode();
            response.put("custom", json != null);
            response.put("enabled", OfficialGroups.isFunctionEnabled(groupId, "member_add_welcome"));
            response.set("config", config);
            ctx.json(Result.success(response));
        } catch (Exception e) {
            log.error("读取群 {} 的欢迎配置失败", groupId, e);
            ctx.status(500).json(Result.fail(500, "读取欢迎配置失败，请检查数据库或现有配置，未对配置作任何修改"));
        }
    }

    public static void save(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        ObjectNode config = readConfig(ctx);
        if (config == null) return;
        if (!GroupRepository.saveJoinWelcomeConfigJson(groupId, config.toString())) {
            ctx.status(500).json(Result.fail(500, "保存失败，请检查数据库后重试"));
            return;
        }
        ctx.json(Result.success(config));
    }

    private static ObjectNode readConfig(Context ctx) {
        if (ctx.body().length() > 131072) {
            ctx.status(413).json(Result.fail(413, "配置不能超过 128 Ki 字符"));
            return null;
        }
        try {
            return JoinWelcomeDAO.validate(MAPPER.readTree(ctx.body()));
        } catch (Exception e) {
            ctx.status(400).json(Result.fail(400, e instanceof IllegalArgumentException ? e.getMessage() : "JSON 格式错误"));
            return null;
        }
    }

    public static void sendTest(Context ctx) {
        ObjectNode config = readConfig(ctx);
        if (config == null) return;
        String debugGroupId = Config.getInstance().getDebugGroupOpenId();
        if (debugGroupId == null || debugGroupId.isBlank() || "null".equalsIgnoreCase(debugGroupId.trim())) {
            ctx.status(400).json(Result.fail(400, "请先在机器人设置中配置 QQ 调试群 OpenId（qq.debug-group-openId）"));
            return;
        }
        long now = System.currentTimeMillis();
        long next = NEXT_TEST_AT.get();
        if (now < next || !NEXT_TEST_AT.compareAndSet(next, now + 3000)) {
            ctx.status(429).json(Result.fail(429, "测试发送过于频繁，请稍等 3 秒再试"));
            return;
        }
        try {
            String text = "@新成员（测试占位） " + config.path("text").asText("");
            String messageId = GroupChat.sendMessage(debugGroupId.trim(), TC.md(text), GroupJoinWelcome.buildWelcomeKeyboard(config));
            if (messageId == null || messageId.isBlank()) {
                ctx.status(502).json(Result.fail(502, "调试群发送失败：官方接口未返回消息 ID，请检查发送日志和主动消息权限"));
                return;
            }
            ctx.json(Result.success(Map.of("messageId", messageId, "debugGroupId", debugGroupId.trim())));
        } catch (QQMessageSendException e) {
            ctx.status(502).json(Result.fail(502, "调试群发送失败：" + e.getMessage()));
        } catch (Exception e) {
            log.error("发送入群欢迎测试到调试群失败", e);
            ctx.status(502).json(Result.fail(502, "调试群发送失败，请检查发送日志和主动消息权限"));
        }
    }

    public static void clear(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        if (!GroupRepository.deleteJoinWelcomeConfig(groupId)) {
            ctx.status(500).json(Result.fail(500, "恢复默认失败，请检查数据库后重试"));
            return;
        }
        ctx.json(Result.success("ok"));
    }

    private static String groupId(Context ctx) {
        String id = ctx.pathParam("groupOpenId");
        if (OfficialGroups.listGroups().stream().noneMatch(group -> group.groupOpenId().equals(id))) {
            ctx.status(404).json(Result.fail(404, "群不存在，请刷新群列表后重试"));
            return null;
        }
        return id;
    }
}
