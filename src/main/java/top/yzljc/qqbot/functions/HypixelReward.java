package top.yzljc.qqbot.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.official.service.CommandButton;
import top.yzljc.qqbot.official.service.QQBotMessageService;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HypixelReward implements CommandExecutor, Listener {
    private static final Logger log = LoggerFactory.getLogger(HypixelReward.class);
    static Settings settings = Config.getInstance();
    private static final String WS_URL = settings.getWebsocketUrl();
    private static HypixelClient client;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(rewards\\.)?hypixel\\.net/claim-reward/[a-zA-Z0-9]+");
    // Key: UUID (sessionId) -> Value: 对应的会话
    private static final ConcurrentHashMap<String, RewardSession> activeSessions = new ConcurrentHashMap<>();

    private static final QQBotMessageService service = AtriBot.getInstance().getMessageService();

    private static RewardSession getSessionByUserId(long userId) {
        if (userId == 0) return null;
        for (RewardSession session : activeSessions.values()) {
            if (session.userId == userId) return session;
        }
        return null;
    }

    private static RewardSession getSessionByUserId(String userOpenId) {
        if (userOpenId == null || userOpenId.isEmpty()) return null;
        for (RewardSession session : activeSessions.values()) {
            if (userOpenId.equals(session.userOpenId)) return session;
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        long messageId = sender.messageId();
        long groupId = sender.groupId();
        long userId = sender.userId();

        String userOpenId = sender.userOpenId();
        String groupOpenId = sender.groupOpenId();
        String messageOpenId = sender.messageOpenId();
        String type = label;

        // === 处理 /cl claim <0|1|2> 指令领奖逻辑 (仅限官方端) ===
        if (args.length >= 2 && args[0].equalsIgnoreCase("claim")) {
            if ("0".equals(type)) {
                return true;
            }

            // 官方端指令领奖逻辑
            RewardSession session = getSessionByUserId(userOpenId);

            if (session == null) {
                String errorText = "> ⚠️ 你没有正在进行的领奖任务喵！";
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            if (session.securityToken == null) {
                String errorText = "> ⚠️ 还未准备好，请稍等喵！";
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            int choice;
            try {
                choice = Integer.parseInt(args[1]);
                if (choice < 0 || choice > 2) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                String errorText = "> ⚠️ 奖励编号无效，请输入 0、1 或 2 喵！";
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "claim");
            request.put("session_id", session.sessionId);
            request.put("choice", choice);
            request.put("security_token", session.securityToken);
            request.put("reward_id", session.rewardId);
            request.put("original_url", session.originalUrl);

            client.send(request.toString());
            log.info("用户 {} (Type:{}) 选择奖励 {} (通过指令)，SessionID: {}", userOpenId, type, choice, session.sessionId);
            return true;
        }

        // === 添加测试数据：跑通全流程 ===
        if (args.length >= 1 && args[0].equalsIgnoreCase("test")) {
            // 只需要label为1 2时过，0不需要
            if ("0".equals(label)) return true;

            if (client == null || !client.isOpen()) {
                String errorText = "> ❌ 服务未连接，请联系管理员启动请求发送端喵！";
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            RewardSession existingSession = getSessionByUserId(userOpenId);
            if (existingSession != null) {
                String errorText = "> ⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！";
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            String sessionId = "test-" + UUID.randomUUID().toString();
            RewardSession session = new RewardSession(
                    sessionId, groupId, userId, messageId,
                    userOpenId, groupOpenId, messageOpenId, type
            );
            activeSessions.put(sessionId, session);

            log.info("用户 {} (Type:{}) 触发[测试流程]，分配 SessionID: {}", userOpenId, type, sessionId);

            // 延时 1 秒，模拟 Python 端返回"解析到奖励，请选择"
            scheduler.schedule(() -> {
                ObjectNode mockSelection = mapper.createObjectNode();
                mockSelection.put("type", "selection_needed");
                mockSelection.put("session_id", sessionId);
                mockSelection.put("security_token", "test_token_123");
                mockSelection.put("reward_id", "test_rid_123");
                mockSelection.put("original_url", "https://rewards.hypixel.net/claim-reward/test");
                mockSelection.putArray("rewards")
                        .add("100,000 街机硬币 (测试数据)")
                        .add("10 个史诗级神秘礼盒 (测试数据)")
                        .add("5 个锦标赛票据 (测试数据)");

                // 复用底层的消息接收机制渲染 Markdown + 按钮
                client.onMessage(mockSelection.toString());

                // 再延时 3 秒，模拟自动跑完了领奖流程
                scheduler.schedule(() -> {
                    if (activeSessions.containsKey(sessionId)) {
                        ObjectNode mockResult = mapper.createObjectNode();
                        mockResult.put("type", "result");
                        mockResult.put("session_id", sessionId);
                        mockResult.put("success", true);
                        mockResult.put("msg", "测试流程跑通：成功领取 100,000 街机硬币 (测试数据)！");

                        client.onMessage(mockResult.toString());
                    }
                }, 3, TimeUnit.SECONDS);

            }, 1, TimeUnit.SECONDS);

            return true;
        }

        String content = args[0];
        Matcher matcher = URL_PATTERN.matcher(content);

        if (matcher.find()) {
            String url = matcher.group();

            if (client == null || !client.isOpen()) {
                String errorText = "> ❌ 服务未连接，请联系管理员启动请求发送端喵！";
                List<List<CommandButton>> layout = List.of(
                        List.of(new CommandButton("c1", "领取新的签到奖励", "/cl ", false, 1, 2))
                );
                Object keyboard = service.buildCmdKeyboard(layout);

                if (label.equals("0")) sender.reply("❌ 服务未连接，请联系管理员启动请求发送端喵！", false);
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText, keyboard);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText, keyboard);
                return true;
            }

            // 判断该用户是否已经在领奖了
            RewardSession existingSession = label.equals("0") ? getSessionByUserId(userId) : getSessionByUserId(userOpenId);
            if (existingSession != null) {
                String errorText = "> ⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！";
                if (label.equals("0")) sender.reply("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！", false);
                if (label.equals("2")) sender.officialGroupReplyMarkdown(errorText);
                if (label.equals("1")) sender.officialPrivateReplyMarkdown(errorText);
                return true;
            }

            String sessionId = UUID.randomUUID().toString();
            RewardSession session = new RewardSession(
                    sessionId, groupId, userId, messageId,
                    userOpenId, groupOpenId, messageOpenId, type
            );
            activeSessions.put(sessionId, session);

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("session_id", sessionId);

            client.send(request.toString());
            log.info("用户 {} (Type:{}) 触发领奖，分配 SessionID: {}",
                    label.equals("0") ? userId : userOpenId, type, sessionId);

        } else {
            if (label.equals("0")) sender.reply("⚠️ 链接格式错误或未检测到链接喵！", false);
            if (label.equals("2")) sender.officialGroupReplyMarkdown("> ⚠️ 链接格式错误或未检测到链接喵！");
            if (label.equals("1")) sender.officialPrivateReplyMarkdown("> ⚠️ 链接格式错误或未检测到链接喵！");
        }
        return true;
    }

    @EventHandler
    public static synchronized void onGroupMessage(GroupMessageEvent event) {
        String rawMessage = event.getRawMessage().trim();
        long groupId = event.getGroupId();
        long userId = event.getUserId();
        long msgId = event.getMessageId();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "get_hypixel_reward")) return;

        RewardSession session = getSessionByUserId(userId);

        if (session != null && session.groupId == groupId) {
            // 普通端 type=0 才会走这里拦截直接发数字的操作
            if (!"0".equals(session.type)) return;

            if (rawMessage.equals("0") || rawMessage.equals("1") || rawMessage.equals("2")) {

                if (session.securityToken == null) {
                    GroupMessage.replyMessage(userId, groupId, msgId, false, "⚠️ 还未准备好，请稍等喵！");
                    return;
                }

                int choice = Integer.parseInt(rawMessage);

                ObjectNode request = mapper.createObjectNode();
                request.put("action", "claim");
                request.put("session_id", session.sessionId);
                request.put("choice", choice);
                request.put("security_token", session.securityToken);
                request.put("reward_id", session.rewardId);
                request.put("original_url", session.originalUrl);

                client.send(request.toString());
                log.info("用户 {} 选择奖励 {} (直接回复)，SessionID: {}", userId, choice, session.sessionId);
            }
        }
    }

    private static class RewardSession {
        String sessionId;
        long groupId;
        long userId;
        long messageId;

        String userOpenId;
        String groupOpenId;
        String messageOpenId;
        String type;

        String securityToken;
        String rewardId;
        String originalUrl;
        long timestamp;

        public RewardSession(String sessionId, long groupId, long userId, long messageId,
                             String userOpenId, String groupOpenId, String messageOpenId, String type) {
            this.sessionId = sessionId;
            this.groupId = groupId;
            this.userId = userId;
            this.messageId = messageId;
            this.userOpenId = userOpenId;
            this.groupOpenId = groupOpenId;
            this.messageOpenId = messageOpenId;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        public void setPythonData(String token, String rid, String url) {
            this.securityToken = token;
            this.rewardId = rid;
            this.originalUrl = url;
        }
    }

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    static {
        startReconnectTask();
    }

    private static void startReconnectTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (client == null || client.isClosed()) connect();

                long now = System.currentTimeMillis();
                activeSessions.entrySet().removeIf(entry -> {
                    RewardSession session = entry.getValue();
                    if (now - session.timestamp > 60000) {
                        switch (session.type) {
                            case "0" -> GroupMessage.chatMessage(session.groupId, "⚠️ 领奖操作超时，请重新获取!");
                            case "2" ->
                                    service.replyGroupMarkdownMessage(session.groupOpenId, session.messageOpenId, "> ⚠️ 领奖操作超时，请重新获取!");
                            case "1" ->
                                    service.replyPrivateMarkdownMessage(session.userOpenId, session.messageOpenId, "> ⚠️ 领奖操作超时，请重新获取!");
                        }
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                // ignore
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static synchronized void connect() {
        try {
            if (client != null && !client.isClosed()) client.close();
            client = new HypixelClient(new URI(WS_URL));
            client.connect();
        } catch (Exception e) {
            log.error("连接 Python 端失败: {}", e.getMessage());
        }
    }

    private static class HypixelClient extends WebSocketClient {
        public HypixelClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            log.info("已连接到 Python 端");
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonNode response = mapper.readTree(message);
                String type = response.path("type").asText();
                String sessionId = response.path("session_id").asText();

                RewardSession session = activeSessions.get(sessionId);
                if (session == null) return;

                long groupId = session.groupId;
                long originMessageId = session.messageId;
                long callbackUserIdLong = session.userId;

                if ("selection_needed".equals(type)) {
                    session.setPythonData(
                            response.path("security_token").asText(),
                            response.path("reward_id").asText(),
                            response.path("original_url").asText()
                    );

                    StringBuilder sb = new StringBuilder("🎁 解析成功！");

                    if ("0".equals(session.type)) {
                        sb.append("请在 1 分钟内直接回复数字 (0-2) 领取：\n");
                    } else {
                        sb.append("请在 1 分钟内点击下方按钮领取奖励：\n");
                    }

                    if (session.type.equals("0")) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append(r.asText()).append("\n");
                        }
                        GroupMessage.replyMessage(callbackUserIdLong, groupId, originMessageId, false, sb.toString());
                    } else if (session.type.equals("2")) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append("> ").append(r.asText()).append("\n");
                        }
                        service.replyGroupMarkdownWithKeyboard(session.groupOpenId, session.messageOpenId, sb.toString(),
                                service.buildCmdKeyboard(List.of(
                                        List.of(
                                                new CommandButton("c1", "领取奖励 [0]", "/cl claim 0", true, 1, 2),
                                                new CommandButton("c2", "领取奖励 [1]", "/cl claim 1", true, 1, 2),
                                                new CommandButton("c3", "领取奖励 [2]", "/cl claim 2", true, 1, 2)
                                        )
                                ))
                        );
                    } else if (session.type.equals("1")) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append("> ").append(r.asText()).append("\n");
                        }
                        service.replyPrivateMarkdownWithKeyboard(session.userOpenId, session.messageOpenId, sb.toString(),
                                service.buildCmdKeyboard(List.of(
                                        List.of(
                                                new CommandButton("c1", "领取奖励 [0]", "/cl claim 0", true, 1, 2),
                                                new CommandButton("c2", "领取奖励 [1]", "/cl claim 1", true, 1, 2),
                                                new CommandButton("c3", "领取奖励 [2]", "/cl claim 2", true, 1, 2)
                                        )
                                ))
                        );
                    }

                } else if ("result".equals(type)) {
                    boolean success = response.path("success").asBoolean();
                    String msg = response.path("msg").asText();
                    String prefix = success ? "🎉 " : "😭 ";

                    switch (session.type) {
                        case "0" -> GroupMessage.chatMessage(groupId, prefix + msg);
                        case "2" ->
                                service.replyGroupMarkdownMessage(session.groupOpenId, session.messageOpenId, prefix + msg);
                        case "1" ->
                                service.replyPrivateMarkdownMessage(session.userOpenId, session.messageOpenId, prefix + msg);
                    }
                    activeSessions.remove(sessionId);

                } else if ("error".equals(type)) {
                    String text = "❌ 出错啦: " + response.path("msg").asText();
                    switch (session.type) {
                        case "0" -> GroupMessage.chatMessage(groupId, text);
                        case "2" -> service.replyGroupMarkdownMessage(session.groupOpenId, session.messageOpenId, text);
                        case "1" ->
                                service.replyPrivateMarkdownMessage(session.userOpenId, session.messageOpenId, text);
                    }
                    activeSessions.remove(sessionId);
                }
            } catch (Exception e) {
                log.error("处理 Python 消息失败: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            activeSessions.clear();
        }

        @Override
        public void onError(Exception ex) {
            activeSessions.clear();
        }
    }
}