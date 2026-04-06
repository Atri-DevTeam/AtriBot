package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;

import java.net.URI;
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

    private static volatile boolean isProcessing = false;
    private static RewardSession currentSession = null;
    private static long messageId = 0;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        messageId = sender.messageId();
        long groupId = sender.groupId();
        long userId = sender.userId();
        String content = args[0];

        Matcher matcher = URL_PATTERN.matcher(content);

        if (matcher.find()) {
            String url = matcher.group();

            if (client == null || !client.isOpen()) {
                sender.reply("❌ 服务未连接，请联系管理员启动请求发送端喵！", false);
                return true;
            }

            if (isProcessing) {
                if (currentSession != null && currentSession.userId == userId) {
                    sender.reply("⚠️ 你已经有一个正在进行的任务了，请先完成或等待超时喵！", false);
                } else {
                    sender.reply("⏳当前有人正在领奖中，请稍等一会再试喵！", false);
                }
                return true;
            }

            // 开始新会话
            isProcessing = true;
            currentSession = new RewardSession(groupId, userId);

            ObjectNode request = mapper.createObjectNode();
            request.put("action", "fetch");
            request.put("url", url);
            request.put("group_id", groupId);
            client.send(request.toString());
            log.info("用户 {} 在群 {} 触发了领奖指令，链接: {}", userId, groupId, url);

        } else {
            sender.reply("⚠️ 链接格式错误或未检测到链接喵！", false);
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

        // 如果是正在进行的会话
        if (isProcessing && currentSession != null) {
            if (currentSession.groupId == groupId && currentSession.userId == userId) {
                if (rawMessage.equals("0") || rawMessage.equals("1") || rawMessage.equals("2")) {

                    if (currentSession.securityToken == null) {
                        MessageUtils.replyMessage(userId, groupId, msgId, false, "⚠️ 还未准备好，请稍等喵！");
                        return;
                    }

                    int choice = Integer.parseInt(rawMessage);

                    ObjectNode request = mapper.createObjectNode();
                    request.put("action", "claim");
                    request.put("group_id", groupId);
                    request.put("choice", choice);
                    request.put("security_token", currentSession.securityToken);
                    request.put("reward_id", currentSession.rewardId);
                    request.put("original_url", currentSession.originalUrl);

                    client.send(request.toString());
                    log.info("用户 {} 在群 {} 选择了奖励 {}，请求已发送数据端", userId, groupId, choice);
                }
            }
        }
    }

    private static class RewardSession {
        long groupId;
        long userId;
        String securityToken;
        String rewardId;
        String originalUrl;
        long timestamp;

        public RewardSession(long groupId, long userId) {
            this.groupId = groupId;
            this.userId = userId;
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
                if (client == null || client.isClosed()) {
                    connect();
                }

                if (isProcessing && currentSession != null) {
                    if (System.currentTimeMillis() - currentSession.timestamp > 60000) {
                        log.warn("领奖操作超时，自动释放锁");
                        if (currentSession.groupId > 0) {
                            MessageSender.sendGroupMessage(currentSession.groupId, "⚠️ 领奖操作超时，请重新获取!");
                        }
                        resetSession();
                    }
                }
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

    private static synchronized void resetSession() {
        currentSession = null;
        isProcessing = false;
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
                long groupId = response.path("group_id").asLong();
                long userId = response.path("user_id").asLong();

                if ("selection_needed".equals(type)) {
                    if (currentSession == null) return; // 异常情况

                    String token = response.path("security_token").asText();
                    String rid = response.path("reward_id").asText();
                    String url = response.path("original_url").asText();

                    currentSession.setPythonData(token, rid, url);

                    StringBuilder sb = new StringBuilder("🎁 解析成功！请在 1 分钟内回复数字 (0-2) 领取：\n");
                    JsonNode rewards = response.path("rewards");
                    for (JsonNode r : rewards) {
                        sb.append(r.asText()).append("\n");
                    }

                    // 使用之前保存的 messageId 或者不需要引用
                    MessageUtils.replyMessage(userId, groupId, messageId, false, sb.toString());

                } else if ("result".equals(type)) {
                    // 最终领取结果
                    boolean success = response.path("success").asBoolean();
                    String msg = response.path("msg").asText();
                    String prefix = success ? "🎉 " : "😭 ";

                    MessageSender.sendGroupMessage(groupId, prefix + msg);
                    resetSession();
                    log.info("领奖流程完成，锁已释放 (Success: {}, Message: {})", success, msg);

                } else if ("error".equals(type)) {
                    String msg = response.path("msg").asText();
                    MessageSender.sendGroupMessage(groupId, "❌ 出错啦: " + msg);
                    resetSession();
                }

            } catch (Exception e) {
                log.error("处理 Python 消息失败: {}", e.getMessage(), e);
                resetSession();
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            resetSession();
        }

        @Override
        public void onError(Exception ex) {
            resetSession();
        }
    }
}