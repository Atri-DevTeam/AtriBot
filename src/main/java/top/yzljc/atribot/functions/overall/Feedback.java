package top.yzljc.atribot.functions.overall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.impl.OfficialC2CMessageEvent;
import top.yzljc.atribot.chat.official.At;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.request.SaSignHeader;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.chat.onebot.UserInformation;
import top.yzljc.atribot.utils.FormatTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author YZ_Ljc_
 * @ClassName Feedback
 * @Created_at 2026/05/12
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions
 */
@Slf4j
public class Feedback implements CommandExecutor, Listener {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path REPLY_STORE_FILE = Path.of("feedback_reply_store.json");
    private static final Pattern contentFormat = Pattern.compile("\\[CQ:[^\\]]*\\]");

    private static final CopyOnWriteArrayList<FeedbackDTO> cachedReplies = new CopyOnWriteArrayList<>();
    private static volatile boolean cacheLoaded = false;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.replyText(label, "请提供反馈内容！用法：/feedback <反馈内容>");
        }

        long groupId = sender.groupId();
        long userId = sender.userId();
        String userName = UserInformation.getUserName(userId);
        String groupName = GroupInformation.getGroupName(groupId);
        long messageId = sender.messageId();
        boolean isGroupAdmin = UserInformation.isGroupAdmin(groupId, userId);
        boolean isBotAdmin = sender.isAdmin();
        boolean isDebugMode = sender.isDebug();

        String groupOpenId = sender.groupOpenId();
        String userOpenId = sender.unionOpenId();
        String messageOpenId = sender.messageOpenId();

        String content = String.join(" ", args);

        Matcher check = contentFormat.matcher(content);
        if (check.find()) {
            sender.replyText(label, "反馈内容必须为纯文本！");
            return true;
        }

        int code = 0;

        if (args.length > 0) {
            if (label.equals("0")) {
                ObjectNode info = mapper.createObjectNode();
                info.put("group_id", groupId);
                info.put("group_name", groupName);
                info.put("user_name", userName);
                info.put("message_id", messageId);
                info.put("is_group_admin", isGroupAdmin);
                info.put("is_bot_admin", isBotAdmin);
                info.put("is_debug_mode", isDebugMode);

                FeedbackRequest data = new FeedbackRequest(content, String.valueOf(userId), 0, info);
                code = submitFeedback(data);
            }

            if (label.equals("1")) {
                ObjectNode info = mapper.createObjectNode();
                info.put("message_open_id", messageOpenId);
                info.put("is_group_admin", isGroupAdmin);
                info.put("is_bot_admin", isBotAdmin);
                info.put("is_debug_mode", isDebugMode);

                FeedbackRequest data = new FeedbackRequest(content, userOpenId, 1, info);
                code = submitFeedback(data);
            }

            if (label.equals("2")) {
                ObjectNode info = mapper.createObjectNode();
                info.put("group_open_id", groupOpenId);
                info.put("message_open_id", messageOpenId);
                info.put("is_group_admin", isGroupAdmin);
                info.put("is_bot_admin", isBotAdmin);
                info.put("is_debug_mode", isDebugMode);

                FeedbackRequest data = new FeedbackRequest(content, userOpenId, 1, info);
                code = submitFeedback(data);
            }
            switch (code) {
                case 200 -> sender.replyText(label, "反馈已收到！我们会尽快处理的~");
                case 201 -> sender.replyText(label, "你已经提交过反馈了哦，请先等待上一条反馈被受理！");
                default -> sender.replyText(label, "反馈提交失败了呢，请稍后再试！");
            }

            String alertStr;
            if (label.equals("0")) {
                alertStr = "收到反馈: " + content + " 来自用户: " + userName + " (QQ: " + userId + ")";
            } else {
                alertStr = "收到反馈: " + content + " 来自用户: " + userOpenId + " (QQ: " + At.at(userOpenId) + ")，来源于群聊: " + sender.groupOpenId();
            }

            GroupMessage.chatMessage(3199590352L, Config.getInstance().getNapcatDebugGroupUin(), alertStr, true);
        }

        return true;
    }

    private static int submitFeedback(FeedbackRequest data) {
        String url = "https://www.yzljc.top/data/api/v2/open-api/feedback/submit";
        String signedUrl = SaSignHeader.sign(url);

        try {
            String jsonBody = mapper.writeValueAsString(data);
            JsonNode response = HttpService.postJson(signedUrl, jsonBody);
            if (response != null) {
                if (response.get("status").asInt() != 200) {
                    if (response.get("status").asInt() == 201) {
                        log.warn("Feedback submission failed: already existed");
                        return 201;
                    } else {
                        log.warn("Feedback submission failed: {}", response.toString());
                        return 500;
                    }
                }
                log.info("Feedback submitted successfully");
                return 200;
            } else {
                log.warn("Feedback submission returned null response");
                return 500;
            }
        } catch (Exception e) {
            log.warn("Failed to submit feedback, error: {}", e.getMessage());
            return 500;
        }
    }

    public static void notifyReply(Context ctx) {
        try {
            if (ctx.body() == null || ctx.body().isBlank()) {
                ctx.status(400).result("empty feedback reply payload");
                return;
            }

            FeedbackDTO data = parseReplyNotification(ctx);
            if (data == null) {
                ctx.status(400).result("invalid feedback reply payload");
                return;
            }

            String id = String.valueOf(data.getId());
            String uuid = data.getUuid();
            String content = data.getContent();
            String replyContent = data.getReplyContent();
            String replier = data.getReplier();
            String provider = data.getProvider();
            Timestamp createdAt = data.getCreatedAt();
            Timestamp repliedAt = data.getRepliedAt();
            long groupId = data.getInfo().path("group_id").asLong(0);

            if (data.getUploadedWay() == 0) {

                String reply = "您的反馈已被受理\n" +
                        "====================\n" +
                        "反馈编号: " + id + "\n" +
                        "UUID: " + uuid + "\n" +
                        "时间: " + createdAt + "\n" +
                        "反馈内容：" + content + "\n" +
                        "====================\n" +
                        "回复人: " + replier + "\n" +
                        "回复时间: " + repliedAt + "\n" +
                        "回复内容: " + replyContent + "\n" +
                        "====================\n" +
                        "如有任何问题欢迎继续联系我们！";

                GroupMessage.chatMessage(Long.parseLong(provider), groupId, reply, true);
                
                log.info("收到反馈回复通知(uploadedWay=0): provider={}, uuid={}", data.getProvider(), data.getUuid());
                ctx.status(200).result("ok");
                return;
            }

            if (data.getUploadedWay() == 1) {
                if (data.getProvider() == null || data.getProvider().isBlank()) {
                    ctx.status(400).result("provider is required when uploadedWay is 1");
                    return;
                }
                storeReplyNotification(data);
                ctx.status(200).result("stored");
                return;
            }

            ctx.status(400).result("unsupported uploadedWay: " + data.getUploadedWay());
        } catch (IOException e) {
            log.warn("反馈回复通知解析失败: {}", e.getMessage(), e);
            ctx.status(400).result("invalid feedback reply payload");
        } catch (Exception e) {
            log.warn("处理反馈回复通知失败: {}", e.getMessage(), e);
            ctx.status(500).result("failed");
        }
    }

    public static FeedbackDTO consumeReplyByProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }

        try {
            ensureCacheLoaded();
            for (FeedbackDTO notification : cachedReplies) {
                if (provider.equals(notification.getProvider())) {
                    cachedReplies.remove(notification);
                    asyncSaveStoredReplies();
                    return notification;
                }
            }
        } catch (Exception e) {
            log.warn("按 provider 读取反馈回复失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private static FeedbackDTO parseReplyNotification(Context ctx) throws IOException {
        JsonNode root = mapper.readTree(ctx.body());
        if (root == null || root.isNull()) {
            return null;
        }

        FeedbackDTO notification = new FeedbackDTO();
        notification.setId(root.path("id").asInt(0));
        notification.setUuid(textValue(root, "uuid"));
        notification.setContent(textValue(root, "content"));
        notification.setReplyContent(textValue(root, "replyContent", "reply_content"));
        notification.setReplier(textValue(root, "replier"));
        notification.setProvider(textValue(root, "provider"));
        notification.setUploadedWay(root.path("uploadedWay").asInt(root.path("uploaded_way").asInt(0)));
        notification.setRead(root.path("isRead").asBoolean(root.path("is_read").asBoolean(false)));

        JsonNode infoNode = root.get("info");
        if (infoNode != null && !infoNode.isNull()) {
            notification.setInfo(infoNode);
        }

        notification.setCreatedAt(FormatTools.parseTimestamp(root, "createdAt", "created_at"));
        notification.setRepliedAt(FormatTools.parseTimestamp(root, "repliedAt", "replied_at"));
        return notification;
    }

    private static void storeReplyNotification(FeedbackDTO notification) throws IOException {
        ensureCacheLoaded();
        cachedReplies.add(notification);
        asyncSaveStoredReplies();
    }

    private static synchronized void ensureCacheLoaded() {
        if (cacheLoaded) {
            return;
        }
        try {
            if (!Files.exists(REPLY_STORE_FILE) || Files.size(REPLY_STORE_FILE) == 0L) {
                cacheLoaded = true;
                return;
            }
            List<FeedbackDTO> notifications = mapper.readValue(
                    REPLY_STORE_FILE.toFile(),
                    new TypeReference<List<FeedbackDTO>>() {}
            );
            if (notifications != null) {
                cachedReplies.addAll(notifications);
            }
            cacheLoaded = true;
        } catch (IOException e) {
            log.error("加载反馈缓存失败", e);
        }
    }

    private static void asyncSaveStoredReplies() {
        List<FeedbackDTO> snapshot = new ArrayList<>(cachedReplies);
        CompletableFuture.runAsync(() -> {
            try {
                Path tempFile = Path.of(REPLY_STORE_FILE + ".tmp");
                mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), snapshot);

                try {
                    Files.move(tempFile, REPLY_STORE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tempFile, REPLY_STORE_FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                log.error("异步保存反馈数据失败", e);
            }
        });
    }

    private static String textValue(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node != null && !node.isNull()) {
                String value = node.asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRequest {
        private String content;
        private String provider;
        private int uploadedWay;
        private JsonNode info;
    }

    @EventHandler
    public void onGroupAtChat(OfficialGroupAtMessageCreateEvent event) {
        FeedbackDTO remaining = consumeReplyByProvider(event.getUnionOpenId());
        if (remaining != null) {
            String reply = "您的反馈已被受理\n\n" +
                    "---\n\n" +
                    "反馈编号: `" + remaining.getId() + "`\n\n" +
                    "UUID: `" + remaining.getUuid() + "`\n\n" +
                    "时间: `" + remaining.getCreatedAt() + "`\n\n" +
                    "反馈内容：`" + remaining.getContent() + "`\n\n" +
                    "---\n\n" +
                    "回复人: `" + remaining.getReplier() + "`\n\n" +
                    "回复时间: `" + remaining.getRepliedAt() + "`\n\n" +
                    "回复内容: `" + remaining.getReplyContent() + "`\n\n" +
                    "---\n\n" +
                    "如有任何问题欢迎继续联系我们！";

            event.sendMessage(TC.md(reply));
            log.info("收到反馈回复通知(事件驱动): provider={}, uuid={}", event.getUnionOpenId(), remaining.getUuid());
        }
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        FeedbackDTO remaining = consumeReplyByProvider(event.getAuthor().getUnionOpenId());
        if (remaining != null) {
            String reply = "您的反馈已被受理\n\n" +
                    "---\n\n" +
                    "反馈编号: `" + remaining.getId() + "`\n\n" +
                    "UUID: `" + remaining.getUuid() + "`\n\n" +
                    "时间: `" + remaining.getCreatedAt() + "`\n\n" +
                    "反馈内容：`" + remaining.getContent() + "`\n\n" +
                    "---\n\n" +
                    "回复人: `" + remaining.getReplier() + "`\n\n" +
                    "回复时间: `" + remaining.getRepliedAt() + "`\n\n" +
                    "回复内容: `" + remaining.getReplyContent() + "`\n\n" +
                    "---\n\n" +
                    "如有任何问题欢迎继续联系我们！";

            event.sendMessage(TC.md(reply));
            log.info("收到反馈回复通知(事件驱动): provider={}, uuid={}", event.getAuthor().getUnionOpenId(), remaining.getUuid());
        }
    }

    @EventHandler
    public void onPrivateChat(OfficialC2CMessageEvent event) {
        FeedbackDTO remaining = consumeReplyByProvider(event.getUnionOpenId());
        if (remaining != null) {
            String reply = "您的反馈已被受理\n\n" +
                    "---\n\n" +
                    "反馈编号: `" + remaining.getId() + "`\n\n" +
                    "UUID: `" + remaining.getUuid() + "`\n\n" +
                    "时间: `" + remaining.getCreatedAt() + "`\n\n" +
                    "反馈内容：`" + remaining.getContent() + "`\n\n" +
                    "---\n\n" +
                    "回复人: `" + remaining.getReplier() + "`\n\n" +
                    "回复时间: `" + remaining.getRepliedAt() + "`\n\n" +
                    "回复内容: `" + remaining.getReplyContent() + "`\n\n" +
                    "---\n\n" +
                    "如有任何问题欢迎继续联系我们！";

            event.sendMessage(TC.md(reply));
            log.info("收到反馈回复通知(事件驱动): provider={}, uuid={}", event.getUnionOpenId(), remaining.getUuid());
        }
    }
}
