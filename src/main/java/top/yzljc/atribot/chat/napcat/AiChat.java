package top.yzljc.atribot.chat.napcat;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.service.ai.AiService;

import java.nio.file.Path;

public final class AiChat {

    private static final GroupRoleplayChat CHAT = new GroupRoleplayChat(
            Path.of("data", "atri-chat"), AiChat::resolveAiService);

    private AiChat() {}

    public static String groupSession(String groupId) {
        return "group:" + GroupChatMemory.normalizeGroupId(groupId);
    }

    public static String groupUserSession(String groupId, String userId) {
        return groupSession(groupId);
    }

    public static String chat(String groupId, String userId, String userMessage) {
        return chat(AiProvider.DEFAULT, groupId, userId, null, userMessage);
    }

    public static String chat(String groupId, String userId, String userName, String userMessage) {
        return chat(AiProvider.DEFAULT, groupId, userId, userName, userMessage);
    }

    public static String chat(AiProvider provider, String groupId, String userId, String userMessage) {
        return chat(provider, groupId, userId, null, userMessage);
    }

    public static String chat(AiProvider provider, String groupId, String userId,
                              String userName, String userMessage) {
        return chat(provider, groupId, userId, userName, "", "", userMessage);
    }

    public static String chat(AiProvider provider, String groupId, String userId, String userName,
                              String messageId, String replyToMessageId, String userMessage) {
        return CHAT.chat(provider, groupId, userId, userName, messageId, replyToMessageId, userMessage);
    }

    public static void observe(String groupId, String userId, String userName, String messageId,
                               String replyToMessageId, String content) {
        CHAT.observe(groupId, userId, userName, messageId, replyToMessageId, content);
    }

    public static void clearContext(String groupId) {
        CHAT.clearContext(groupId);
    }

    public static void recordSentReply(String groupId, String userId, String sourceMessageId, String sentMessageId) {
        CHAT.recordSentReply(groupId, userId, sourceMessageId, sentMessageId);
    }

    public static void clearContext(String groupId, String userId) {
        clearContext(groupId);
    }

    public static int contextSize(String groupId, String userId) {
        return CHAT.contextSize(groupId);
    }

    public static void clearAllContext() {
        CHAT.clearAllContext();
    }

    private static AiService resolveAiService() {
        Atri atri = Atri.getInstance();
        return atri == null ? null : atri.getAiService();
    }
}
