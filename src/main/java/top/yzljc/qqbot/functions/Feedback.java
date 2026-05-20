package top.yzljc.qqbot.functions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.data.FeedbackData;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.request.SaSignHeader;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.utils.FormatTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName Feedback
 * @Created_at 2026/05/12
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions
 */
@Slf4j
public class Feedback implements CommandExecutor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path REPLY_STORE_FILE = Path.of("feedback_reply_store.json");
    private static final Pattern contentFormat = Pattern.compile("\\[CQ:[^\\]]*\\]");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.replyText(label, "请提供反馈内容！\n用法：/feedback <反馈内容>");
        }

        long groupId = sender.groupId();
        long userId = sender.userId();
        String userName = GetUserInfo.getUserName(userId);
        String groupName = GetGroupInfo.getGroupName(groupId);
        long messageId = sender.messageId();
        boolean isGroupAdmin = GetUserInfo.isGroupAdmin(groupId, userId);
        boolean isBotAdmin = sender.isAdmin();
        boolean isDebugMode = sender.isDebug();

        String groupOpenId = sender.groupOpenId();
        String userOpenId = sender.userOpenId();
        String messageOpenId = sender.messageOpenId();

        String content = String.join(" ", args);

        Matcher check = contentFormat.matcher(content);
        if (check.find()) {
            sender.replyText(label, "反馈内容必须为纯文本！");
            return true;
        }

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
                submitFeedback(data);
            }

            if (label.equals("1")) {
                ObjectNode info = mapper.createObjectNode();
                info.put("message_open_id", messageOpenId);
                info.put("is_group_admin", isGroupAdmin);
                info.put("is_bot_admin", isBotAdmin);
                info.put("is_debug_mode", isDebugMode);

                FeedbackRequest data = new FeedbackRequest(content, userOpenId, 1, info);
                submitFeedback(data);
            }

            if (label.equals("2")) {
                ObjectNode info = mapper.createObjectNode();
                info.put("group_open_id", groupOpenId);
                info.put("message_open_id", messageOpenId);
                info.put("is_group_admin", isGroupAdmin);
                info.put("is_bot_admin", isBotAdmin);
                info.put("is_debug_mode", isDebugMode);

                FeedbackRequest data = new FeedbackRequest(content, userOpenId, 1, info);
                submitFeedback(data);
            }
            sender.replyText(label, "反馈已收到！我们会尽快处理的~");
            GroupMessage.chatMessage(3199590352L, Config.getInstance().getDebugGroupId(), "收到反馈: " + content + " 来自用户: " + userName + " (QQ: " + userId + ")", true);
        }

        return true;
    }

    private static void submitFeedback(FeedbackRequest data) {
        String url = "https://www.yzljc.top/data/api/v2/open-api/feedback/submit";
        String signedUrl = SaSignHeader.sign(url);

        try {
            String jsonBody = mapper.writeValueAsString(data);
            JsonNode response = HttpService.postJson(signedUrl, jsonBody);
            if (response != null) {
                log.info("Feedback submitted successfully");
            } else {
                log.warn("Feedback submission returned null response");
            }
        } catch (Exception e) {
            log.warn("Failed to submit feedback, error: {}", e.getMessage());
        }
    }

    public static void notifyReply(Context ctx) {
        try {
            if (ctx.body() == null || ctx.body().isBlank()) {
                ctx.status(400).result("empty feedback reply payload");
                return;
            }

            FeedbackData data = parseReplyNotification(ctx);
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

    public static synchronized FeedbackData consumeReplyByProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }

        try {
            List<FeedbackData> notifications = loadStoredReplies();
            for (Iterator<FeedbackData> it = notifications.iterator(); it.hasNext(); ) {
                FeedbackData notification = it.next();
                if (provider.equals(notification.getProvider())) {
                    it.remove();
                    saveStoredReplies(notifications);
                    return notification;
                }
            }
        } catch (Exception e) {
            log.warn("按 provider 读取反馈回复失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private static FeedbackData parseReplyNotification(Context ctx) throws IOException {
        JsonNode root = mapper.readTree(ctx.body());
        if (root == null || root.isNull()) {
            return null;
        }

        FeedbackData notification = new FeedbackData();
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

    private static synchronized void storeReplyNotification(FeedbackData notification) throws IOException {
        List<FeedbackData> notifications = loadStoredReplies();
        notifications.add(notification);
        saveStoredReplies(notifications);
    }

    private static List<FeedbackData> loadStoredReplies() throws IOException {
        if (!Files.exists(REPLY_STORE_FILE)) {
            return new ArrayList<>();
        }

        if (Files.size(REPLY_STORE_FILE) == 0L) {
            return new ArrayList<>();
        }

        List<FeedbackData> notifications = mapper.readValue(
                REPLY_STORE_FILE.toFile(),
                new TypeReference<List<FeedbackData>>() {}
        );
        return notifications == null ? new ArrayList<>() : notifications;
    }

    private static void saveStoredReplies(List<FeedbackData> notifications) throws IOException {
        Path tempFile = Path.of(REPLY_STORE_FILE.toString() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), notifications);

        try {
            Files.move(tempFile, REPLY_STORE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempFile, REPLY_STORE_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
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
}
