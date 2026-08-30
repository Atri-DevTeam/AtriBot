package top.yzljc.atribot.function.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.*;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.HypixelRewardAutoClaim;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HypixelRewardCommand implements CommandExecutor, Listener {
    private static final Logger log = LoggerFactory.getLogger(HypixelRewardCommand.class);
    private static final String WS_URL = Config.getInstance().getHypixelRewardWebSocketUrl();
    private static HypixelClient client;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:rewards\\.)?hypixel\\.net/claim-reward/([a-zA-Z0-9]+)");
    private static final long SESSION_TIMEOUT_MILLIS = 60000;
    // Key: UUID (sessionId) -> Value: 对应的会话
    private static final ConcurrentHashMap<String, RewardSession> activeSessions = new ConcurrentHashMap<>();
    // Key: Hypixel 领奖 ID -> Value: 占用该 ID 的 sessionId
    private static final ConcurrentHashMap<String, String> activeRewardIds = new ConcurrentHashMap<>();

    private static RewardSession getSessionByUserId(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        for (RewardSession session : activeSessions.values()) {
            if (userId.equals(session.userId)) return session;
        }
        return null;
    }

    private static RewardSession getSessionByRewardId(String rewardId) {
        if (rewardId == null || rewardId.isEmpty()) return null;
        String sessionId = activeRewardIds.get(rewardId);
        if (sessionId == null) return null;

        RewardSession session = activeSessions.get(sessionId);
        if (session == null) {
            activeRewardIds.remove(rewardId, sessionId);
        }
        return session;
    }

    private static RewardSession createSession(String userId, String groupId, String messageId, Platform platform, String rewardId) {
        while (true) {
            String sessionId = UUID.randomUUID().toString();
            RewardSession session = new RewardSession(sessionId, userId, groupId, messageId, platform, rewardId);
            activeSessions.put(sessionId, session);

            String existingSessionId = activeRewardIds.putIfAbsent(rewardId, sessionId);
            if (existingSessionId == null) return session;

            activeSessions.remove(sessionId, session);
            if (activeSessions.containsKey(existingSessionId)) return null;
            activeRewardIds.remove(rewardId, existingSessionId);
        }
    }

    private static String getRewardIdLockedMessage(RewardSession session) {
        long remainingMillis = Math.max(0, SESSION_TIMEOUT_MILLIS - (System.currentTimeMillis() - session.timestamp));
        long remainingSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        return "⚠️ 这个领奖ID已经被其他用户使用中，请等待 " + remainingSeconds + " 秒后再试喵！";
    }

    private static void removeSession(String sessionId) {
        RewardSession session = activeSessions.remove(sessionId);
        if (session != null) {
            activeRewardIds.remove(session.claimId, sessionId);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        String groupId;
        String userId = sender.getUserId();
        String messageId;
        Platform platform;
        if (sender instanceof NapcatCommandSender nc) {
            groupId = nc.getGroupId();
            messageId = nc.getMessage().getMessageId();
            platform = nc.getPlatform();
        } else if (sender instanceof QQCommandSender qq) {
            groupId = qq.getGroupId();
            messageId = qq.getMessage().getMessageId();
            platform = qq.getPlatform();
        } else {
            return true;
        }

        if (platform == Platform.NAPCAT_GROUP && !GroupConfigManager.isFeatureEnabled(groupId, "get_hypixel_reward")) return true;

        String content = args[0];
        Matcher matcher = URL_PATTERN.matcher(content);

        if (matcher.find()) {
            String url = matcher.group();
            String rewardId = matcher.group(1);

            if (client == null || !client.isOpen()) {
                String errorText = "> 请求失败，请向开发者报告此问题！";
                Object keyboard = TC.keyboard(List.of(
                        List.of(new Button("c1", "领取新的签到奖励", "/cl ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));

                if (sender instanceof QQCommandSender qq) {
                    qq.sendMessage(TC.md(errorText), keyboard);
                } else {
                    sender.sendMessage(errorText);
                }
                return true;
            }

            // 判断该用户是否已经在领奖了
            RewardSession existingSession = getSessionByUserId(userId);
            if (existingSession != null) {
                sender.sendMessage("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！");
                return true;
            }

            RewardSession rewardIdSession = getSessionByRewardId(rewardId);
            if (rewardIdSession != null && !userId.equals(rewardIdSession.userId)) {
                sender.sendMessage(getRewardIdLockedMessage(rewardIdSession));
                return true;
            }

            RewardSession session = createSession(userId, groupId, messageId, platform, rewardId);
            if (session == null) {
                rewardIdSession = getSessionByRewardId(rewardId);
                sender.sendMessage(rewardIdSession == null ? "⚠️ 这个领奖ID正在被使用，请稍后再试喵！" : getRewardIdLockedMessage(rewardIdSession));
                return true;
            }

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("session_id", session.sessionId);

            client.send(request.toString());
            log.info("用户 {} (Platform:{}) 触发领奖，分配 SessionID: {}", userId, session.platform, session.sessionId);

        } else {
            sender.sendMessage("⚠️ 链接格式错误或未检测到链接喵！");
        }
        return true;
    }

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        String rawMessage = event.getMessage().getContent().trim();
        String groupId = event.getGroupId();
        String userId = event.getUser().getUserId();
        String msgId = event.getMessage().getMessageId();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "get_hypixel_reward")) return;

        RewardSession session = getSessionByUserId(userId);

        if (session != null && groupId.equals(session.groupId)) {
            // Napcat 群聊才会走这里拦截直接发数字的操作
            if (session.platform != Platform.NAPCAT_GROUP) return;

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

    @EventHandler
    public void onOfficialGroupMessageCreate(OfficialGroupMessageCreateEvent event) {
        if (event.shouldIgnore()) return;
        String content = event.getMessage().getContent();
        if (content.contains("/cl ")) return;
        Matcher findLink = URL_PATTERN.matcher(content.trim());
        if (findLink.find()) {
            String url = findLink.group();
            String rewardId = findLink.group(1);

            if (client == null || !client.isOpen()) {
                String errorText = "> 请求失败，请向开发者报告此问题！";
                Object keyboard = TC.keyboard(List.of(
                        List.of(new Button("c1", "领取新的签到奖励", "/cl ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));

                event.sendMessage(TC.md(errorText), keyboard);
                return;
            }

            // 判断该用户是否已经在领奖了
            RewardSession existingSession = getSessionByUserId(event.getUser().getUserId());
            if (existingSession != null) {
                event.sendMessage("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！");
                return;
            }

            RewardSession rewardIdSession = getSessionByRewardId(rewardId);
            if (rewardIdSession != null && !event.getUser().getUserId().equals(rewardIdSession.userId)) {
                event.sendMessage(getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            RewardSession session = createSession(
                    event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId(), Platform.OFFICIAL_GROUP, rewardId
            );
            if (session == null) {
                rewardIdSession = getSessionByRewardId(rewardId);
                event.sendMessage(rewardIdSession == null ? "⚠️ 这个领奖ID正在被使用，请稍后再试喵！" : getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("session_id", session.sessionId);

            client.send(request.toString());
            log.info("用户 {} (Platform:{}) 触发领奖，分配 SessionID: {}", event.getUser().getUsername(), Platform.OFFICIAL_GROUP, session.sessionId);
        }
    }

    @EventHandler
    public void onOfficialC2CMessageCreate(OfficialC2CMessageCreateEvent event) {
        if (event.shouldIgnore()) return;
        String content = event.getMessage().getContent();
        if (content.contains("/cl ")) return;
        Matcher findLink = URL_PATTERN.matcher(content.trim());
        if (findLink.find()) {
            String url = findLink.group();
            String rewardId = findLink.group(1);

            if (client == null || !client.isOpen()) {
                String errorText = "> 请求失败，请向开发者报告此问题！";
                Object keyboard = TC.keyboard(List.of(
                        List.of(new Button("c1", "领取新的签到奖励", "/cl ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));

                event.sendMessage(TC.md(errorText), keyboard);
                return;
            }

            RewardSession existingSession = getSessionByUserId(event.getUser().getUserId());
            if (existingSession != null) {
                event.sendMessage("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！");
                return;
            }

            RewardSession rewardIdSession = getSessionByRewardId(rewardId);
            if (rewardIdSession != null && !event.getUser().getUserId().equals(rewardIdSession.userId)) {
                event.sendMessage(getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            RewardSession session = createSession(
                    event.getUser().getUserId(), "null", event.getMessage().getMessageId(), Platform.OFFICIAL_C2C, rewardId
            );
            if (session == null) {
                rewardIdSession = getSessionByRewardId(rewardId);
                event.sendMessage(rewardIdSession == null ? "⚠️ 这个领奖ID正在被使用，请稍后再试喵！" : getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("session_id", session.sessionId);

            client.send(request.toString());
            log.info("用户 {} (Type:{}) 触发领奖，分配 SessionID: {}", event.getUser().getUsername(), "2", session.sessionId);
        }
    }

    @EventHandler
    public void onNapcatGroupMessage(NapcatGroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "get_hypixel_reward")) return;
        var content = event.getMessage().getContent();
        if (content.contains("/cl ")) return;
        Matcher findLink = URL_PATTERN.matcher(content.trim());
        if (findLink.find()) {
            String url = findLink.group();
            String rewardId = findLink.group(1);

            if (client == null || !client.isOpen()) {
                event.sendMessage("请求失败，请向开发者报告此问题！");
                return;
            }

            RewardSession existingSession = getSessionByUserId(event.getUser().getUserId());
            if (existingSession != null) {
                event.sendMessage("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！");
                return;
            }

            RewardSession rewardIdSession = getSessionByRewardId(rewardId);
            if (rewardIdSession != null && !event.getUser().getUserId().equals(rewardIdSession.userId)) {
                event.sendMessage(getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            RewardSession session = createSession(
                    event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId(), Platform.NAPCAT_GROUP, rewardId
            );
            if (session == null) {
                rewardIdSession = getSessionByRewardId(rewardId);
                event.sendMessage(rewardIdSession == null ? "⚠️ 这个领奖ID正在被使用，请稍后再试喵！" : getRewardIdLockedMessage(rewardIdSession));
                return;
            }

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("session_id", session.sessionId);

            client.send(request.toString());
            log.info("用户 {} (Type:{}) 触发领奖，分配 SessionID: {}", event.getUser().getUsername(), "2", session.sessionId);
        }
    }

    private static class RewardSession {
        String sessionId;
        String userId;
        String groupId;
        String messageId;
        Platform platform;

        String claimId;
        String securityToken;
        String rewardId;
        String originalUrl;
        long timestamp;

        public RewardSession(String sessionId, String userId, String groupId, String messageId, Platform platform, String claimId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.groupId = groupId;
            this.messageId = messageId;
            this.platform = platform;
            this.claimId = claimId;
            this.timestamp = System.currentTimeMillis();
        }

        public void setPythonData(String token, String rid, String url) {
            this.securityToken = token;
            this.rewardId = rid;
            this.originalUrl = url;
        }
    }

    public static boolean claimReward(RewardSession session, int choice) {
        if (session == null || session.securityToken == null) return false;
        if (choice < 0 || choice > 2) return false;
        if (client == null || !client.isOpen()) return false;

        ObjectNode request = mapper.createObjectNode();
        request.put("action", "claim");
        request.put("session_id", session.sessionId);
        request.put("choice", choice);
        request.put("security_token", session.securityToken);
        request.put("reward_id", session.rewardId);
        request.put("original_url", session.originalUrl);

        client.send(request.toString());

        log.info("用户 {} (Platform:{}) 选择奖励 {}，SessionID: {}", session.userId, session.platform, choice, session.sessionId);
        return true;
    }

    @EventHandler
    public void callback(OfficialButtonInteractionEvent event) {
        if (event.getType() != 11) return;
        if (!event.getButtonValue().equals("reward_claim")) return;

        String buttonId = event.getButtonId();
        RewardSession session = getSessionByUserId(event.getUserOpenId());
        if (session == null || session.userId == null || !Objects.equals(session.userId, event.getUserOpenId())) {
            event.answer(AnswerCode.NO_PERMISSION);
            return;
        }
        switch (buttonId) {
            case "c0" -> event.answer(claimReward(session, 0) ? AnswerCode.SUCCESS : AnswerCode.FAIL);
            case "c1" -> event.answer(claimReward(session, 1) ? AnswerCode.SUCCESS : AnswerCode.FAIL);
            case "c2" -> event.answer(claimReward(session, 2) ? AnswerCode.SUCCESS : AnswerCode.FAIL);
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
                    if (now - session.timestamp > SESSION_TIMEOUT_MILLIS) {
                        switch (session.platform) {
                            case NAPCAT_GROUP -> GroupMessage.chatMessage(session.groupId, "⚠️ 领奖操作超时，请重新获取!");
                            case OFFICIAL_GROUP ->
                                    GroupChat.replyMessage(session.groupId, session.messageId, "⚠️ 领奖操作超时，请重新获取!");
                            case OFFICIAL_C2C ->
                                    C2CChat.replyMessage(session.userId, session.messageId, "⚠️ 领奖操作超时，请重新获取!");
                        }
                        activeRewardIds.remove(session.claimId, entry.getKey());
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                // ignore
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public static void shutdown() {
        scheduler.shutdownNow();
        if (client != null) {
            client.close();
        }
        activeSessions.clear();
        activeRewardIds.clear();
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

                if ("selection_needed".equals(type)) {
                    session.setPythonData(
                            response.path("security_token").asText(),
                            response.path("reward_id").asText(),
                            response.path("original_url").asText()
                    );

                    boolean isAutoClaimEnabled = HypixelRewardAutoClaim.isAutoClaimEnabled(session.userId);

                    StringBuilder sb = new StringBuilder("🎁 解析成功！");

                    if (session.platform == Platform.NAPCAT_GROUP) {
                        sb.append("请在 1 分钟内直接回复数字 (0-2) 领取：\n");
                    } else {
                        if (!isAutoClaimEnabled) {
                            sb.append("请在 1 分钟内点击下方按钮领取奖励：\n");
                        } else {
                            sb.append("自动领取已启用，等待领取中...\n");
                        }
                    }

                    if (session.platform == Platform.NAPCAT_GROUP) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append(r.asText()).append("\n");
                        }
                        sb.append("使用机器人3889798968以获得更佳体验！");
                        GroupMessage.replyMessage(session.userId, session.groupId, session.messageId, false, sb.toString());
                    } else if (session.platform == Platform.OFFICIAL_GROUP) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append("> ").append(getColoredItem(r.asText())).append("\n");
                        }
                        sb.append("> ").append(Markdown.enterCommand("/preferences hypixel_reward", "设置自动领取规则"));
                        if (!OfficialGroups.allowProactiveMsg(session.groupId)) {
                            sb.append("\n").append(Markdown.enterCommand("/全量消息", "/全量消息")).append("授权后可直接解析链接");
                        }
                            if (isAutoClaimEnabled) {
                            GroupChat.replyMessage(session.groupId, session.userId, session.messageId, TC.md(sb.toString()));
                        } else {
                            GroupChat.replyMessage(session.groupId, session.userId,
                                    session.messageId,
                                    TC.md(sb.toString()),
                                    TC.keyboard(List.of(
                                            List.of(
                                                    new Button("c0", "奖励 [0]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取").setAllowedOpenIds(List.of(session.userId)).setPermissionType(PermissionType.SPECIFIC_USER),
                                                    new Button("c1", "奖励 [1]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取").setAllowedOpenIds(List.of(session.userId)).setPermissionType(PermissionType.SPECIFIC_USER),
                                                    new Button("c2", "奖励 [2]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取").setAllowedOpenIds(List.of(session.userId)).setPermissionType(PermissionType.SPECIFIC_USER)
                                            )
                                    ))
                            );
                        }
                    } else if (session.platform == Platform.OFFICIAL_C2C) {
                        for (JsonNode r : response.path("rewards")) {
                            sb.append("> ").append(getColoredItem(r.asText())).append("\n");
                        }
                        sb.append("> ").append(Markdown.enterCommand("/preferences hypixel_reward", "设置自动领取规则"));
                        if (isAutoClaimEnabled) {
                            C2CChat.replyMessage(session.userId, session.messageId, TC.md(sb.toString()));
                        } else {
                            C2CChat.replyMessage(session.userId, session.messageId,
                                    TC.md(sb.toString()),
                                    TC.keyboard(List.of(
                                            List.of(
                                                    new Button("c0", "奖励 [0]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取"),
                                                    new Button("c1", "奖励 [1]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取"),
                                                    new Button("c2", "奖励 [2]", "reward_claim", true, ButtonStyle.BLUE, ButtonType.CALLBACK).setVisitedDisplayText("已领取")
                                            )
                                    ))
                            );
                        }
                    }

                    if (HypixelRewardAutoClaim.isAutoClaimEnabled(session.userId)) {
                        // 自动领取：官机用户开启自动模式后，按其偏好设置自动选择并领取（界面仍照常展示）
                        if (session.platform == Platform.OFFICIAL_GROUP || session.platform == Platform.OFFICIAL_C2C) {
                            List<String> rewardLines = new ArrayList<>();
                            response.path("rewards").forEach(r -> rewardLines.add(r.asText()));
                            Integer choice = HypixelRewardAutoClaim.selectReward(session.userId, rewardLines);
                            if (choice != null) {
                                claimReward(session, choice);
                                log.info("用户 {} 自动领取已选择奖励 [{}]，SessionID: {}", session.userId, choice, session.sessionId);
                            }
                        }
                    }

                } else if ("result".equals(type)) {
                    boolean success = response.path("success").asBoolean();
                    String msg = response.path("msg").asText();
                    String prefix = success ? "🎉 " : "😭 ";
                    String rewardUrl = "https://rewards.hypixel.net/claim-reward/" + session.rewardId + "/banner.png";
                    Map<String, Object> body = Map.of(
                            "cover", Map.of("x1", "408", "y1", "283", "x2", "764", "y2", "362", "color", "#dbae2f"),
                            "url", rewardUrl
                    );
                    ImageDTO dto = PreImageGenerate.dump(body);
                    String finalUrl = null;
                    if (dto != null) {
                        finalUrl = dto.url();
                    }

                    Object keyboard = TC.keyboard(List.of(List.of(
                            new Button("c0", "再领取一个", "/cl ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                    )));

                    switch (session.platform) {
                        case NAPCAT_GROUP -> GroupMessage.chatMessage(session.groupId, prefix + msg);
                        case OFFICIAL_GROUP -> {
                            if (finalUrl != null) {
                                GroupChat.replyMessage(session.groupId, session.userId,
                                        session.messageId,
                                        TC.md(prefix + msg + "\n\n" + Markdown.img(finalUrl, 764, 399)), keyboard);
                            } else {
                                GroupChat.replyMessage(session.groupId, session.userId,
                                        session.messageId,
                                        TC.md(prefix + msg), keyboard);
                            }
                        }
                        case OFFICIAL_C2C -> {
                            if (finalUrl != null) {
                                C2CChat.replyMessage(session.userId, session.messageId,
                                        TC.md(prefix + msg + "\n\n" + Markdown.img(finalUrl, 764, 399)), keyboard);
                            } else {
                                C2CChat.replyMessage(session.userId, session.messageId,
                                        TC.md(prefix + msg), keyboard);
                            }
                        }
                    }
                    removeSession(sessionId);

                } else if ("error".equals(type)) {
                    String text = "执行操作时出现错误: " + response.path("msg").asText();
                    switch (session.platform) {
                        case NAPCAT_GROUP -> GroupMessage.chatMessage(session.groupId, text);
                        case OFFICIAL_GROUP -> GroupChat.replyMessage(session.groupId, session.messageId, text);
                        case OFFICIAL_C2C -> C2CChat.replyMessage(session.userId, session.messageId, text);
                    }
                    removeSession(sessionId);
                }
            } catch (Exception e) {
                log.error("处理 Python 消息失败: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            activeSessions.clear();
            activeRewardIds.clear();
        }

        @Override
        public void onError(Exception ex) {
            activeSessions.clear();
            activeRewardIds.clear();
        }
    }

    public static final Pattern REWARD_LINE_PATTERN = Pattern.compile("^(\\[\\d+])\\s+([A-Za-z]+):\\s*(.*)$");
    public static final Pattern REWARD_KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static String getColoredItem(String rawContent) {
        Matcher lineMatcher = REWARD_LINE_PATTERN.matcher(rawContent);
        if (!lineMatcher.matches()) return rawContent;

        String rarity = lineMatcher.group(2).toUpperCase(Locale.ROOT);
        String color = rarityColors.get(rarity);
        if (color == null) return rawContent;

        String coloredRarity = "$\\textcolor{" + color + "}{\\text{" + rarityNames.get(rarity) + "}}$";
        return lineMatcher.group(1) + " " + coloredRarity + ": " + replaceItemKeys(lineMatcher.group(3));
    }

    private static String replaceItemKeys(String content) {
        Matcher keyMatcher = REWARD_KEY_PATTERN.matcher(content);
        StringBuilder translated = new StringBuilder();
        while (keyMatcher.find()) {
            String key = keyMatcher.group();
            String replacement = itemNamespace.get(key);
            if (replacement == null) replacement = itemNamespace.get(key.toLowerCase(Locale.ROOT));
            if (replacement == null) replacement = itemNamespace.get(key.toUpperCase(Locale.ROOT));
            keyMatcher.appendReplacement(translated, Matcher.quoteReplacement(replacement == null ? key : replacement));
        }
        keyMatcher.appendTail(translated);
        return translated.toString();
    }

    private static final Map<String, String> rarityColors = Map.of(
            "COMMON", "#9D9D9D",
            "RARE", "#0070DD",
            "EPIC", "#A335EE",
            "LEGENDARY", "#FF8000"
    );

    private static final Map<String, String> rarityNames = Map.of(
            "COMMON", "普通",
            "RARE", "稀有",
            "EPIC", "史诗",
            "LEGENDARY", "传说"
    );

    public static final Map<String, String> itemNamespace = Map.ofEntries(
            Map.entry("dust", "神秘之尘"),
            Map.entry("souls", "空岛战争灵魂"),
            Map.entry("tokens", "代币"),
            Map.entry("WALLS3", "超级战墙"),
            Map.entry("coins", "硬币"),
            Map.entry("UHC", "极限生存冠军"),
            Map.entry("experience", "大厅经验"),
            Map.entry("QUAKECRAFT", "未来射击"),
            Map.entry("SUPER_SMASH", "星碎英雄"),
            Map.entry("WALLS", "经典战墙"),
            Map.entry("BATTLEGROUND", "战争领主"),
            Map.entry("PAINTBALL", "彩蛋射击"),
            Map.entry("BUILD_BATTLE", "建筑大师"),
            Map.entry("BEDWARS", "起床战争"),
            Map.entry("ARCADE", "街机游戏"),
            Map.entry("ARENA", "竞技场乱斗"),
            Map.entry("DUELS", "决斗游戏"),
            Map.entry("MCGO", "警匪大战"),
            Map.entry("LEGACY", "经典游戏"),
            Map.entry("VAMPIREZ", "吸血鬼大战"),
            Map.entry("TNTGAMES", "TNT游戏"),
            Map.entry("MURDER_MYSTERY", "密室杀手"),
            Map.entry("adsense_token", "每日奖励代币"),
            Map.entry("SURVIVAL_GAMES", "闪电饥饿游戏"),
            Map.entry("housing_package", "家园世界装饰品"),
            Map.entry("GINGERBREAD", "卡丁车竞赛"),
            Map.entry("SKYWARS", "空岛战争")
    );
}
