package top.yzljc.atribot.chat.napcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.service.ai.AiService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName AiChat
 * @Created_at 2026/07/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public final class AiChat {

    private static final Logger log = LoggerFactory.getLogger(AiChat.class);

    private static final int DEFAULT_MAX_TURNS = 12;
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private static final int MAX_PROMPT_LENGTH = 8000;
    private static final Duration SESSION_TTL = Duration.ofHours(6);

    private static final Map<String, Conversation> CONVERSATIONS = new ConcurrentHashMap<>();

    private AiChat() {
    }

    public static String groupUserSession(String groupId, String userId) {
        return "group:" + normalizeId(groupId) + ":user:" + normalizeId(userId);
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

    public static String chat(AiProvider provider, String groupId, String userId, String userName, String userMessage) {
        AiService aiService = resolveAiService();
        if (aiService == null) {
            log.warn("AiService 未初始化，无法处理 NapCat AI 对话");
            return "我的高性能大脑还没有启动好，请稍后再试呀~";
        }

        String sessionId = groupUserSession(groupId, userId);
        String normalizedMessage = normalizeMessage(userMessage);
        if (normalizedMessage.isEmpty()) {
            return "你还没有告诉我要聊什么呢。";
        }

        cleanupExpiredSessions();

        Conversation conversation = CONVERSATIONS.computeIfAbsent(
                sessionId,
                ignored -> new Conversation(DEFAULT_MAX_TURNS)
        );

        UserInput input = new UserInput(userId, userName, normalizedMessage);
        String prompt = conversation.buildPrompt(input);
        String reply = aiService.ask(Objects.requireNonNullElse(provider, AiProvider.DEFAULT), prompt);
        if (AiService.isValidResponse(reply)) {
            conversation.add(input, reply);
        }
        return reply;
    }

    public static void clearContext(String groupId, String userId) {
        CONVERSATIONS.remove(groupUserSession(groupId, userId));
    }

    public static int contextSize(String groupId, String userId) {
        Conversation conversation = CONVERSATIONS.get(groupUserSession(groupId, userId));
        return conversation == null ? 0 : conversation.size();
    }

    public static void clearAllContext() {
        CONVERSATIONS.clear();
    }

    private static AiService resolveAiService() {
        Atri atri = Atri.getInstance();
        return atri == null ? null : atri.getAiService();
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.trim();
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.trim();
        if (normalized.length() <= MAX_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static void cleanupExpiredSessions() {
        Instant now = Instant.now();
        CONVERSATIONS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record UserInput(String userId, String userName, String content) {

        String displayName() {
            if (userName != null && !userName.isBlank()) {
                return userName.trim();
            }
            if (userId != null && !userId.isBlank()) {
                return userId.trim();
            }
            return "用户";
        }
    }

    private record Turn(String userName, String userMessage, String assistantMessage) {
    }

    private static final class Conversation {

        private final int maxTurns;
        private final Deque<Turn> turns = new ArrayDeque<>();
        private Instant lastAccess = Instant.now();

        private Conversation(int maxTurns) {
            this.maxTurns = maxTurns;
        }

        private synchronized String buildPrompt(UserInput input) {
            lastAccess = Instant.now();

            StringBuilder prompt = new StringBuilder();
            if (!turns.isEmpty()) {
                prompt.append("\n最近对话：\n");
                appendRecentTurns(prompt);
            }

            prompt.append("\n当前消息：\n")
                    .append(input.displayName())
                    .append(": ")
                    .append(input.content())
                    .append("\n\n亚托莉：");

            return prompt.toString();
        }

        private void appendRecentTurns(StringBuilder prompt) {
            Iterator<Turn> iterator = turns.descendingIterator();
            Deque<Turn> selected = new ArrayDeque<>();

            while (iterator.hasNext()) {
                Turn turn = iterator.next();
                selected.addFirst(turn);
                if (promptLengthWith(prompt, selected) > MAX_PROMPT_LENGTH) {
                    selected.removeFirst();
                    break;
                }
            }

            for (Turn turn : selected) {
                prompt.append(turn.userName()).append(": ").append(turn.userMessage()).append('\n');
                prompt.append("亚托莉: ").append(turn.assistantMessage()).append('\n');
            }
        }

        private int promptLengthWith(StringBuilder prompt, Deque<Turn> selected) {
            int length = prompt.length();
            for (Turn turn : selected) {
                length += turn.userName().length() + turn.userMessage().length() + turn.assistantMessage().length() + 12;
            }
            return length;
        }

        private synchronized void add(UserInput input, String reply) {
            lastAccess = Instant.now();
            turns.addLast(new Turn(input.displayName(), input.content(), reply == null ? "" : reply.trim()));
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
        }

        private synchronized int size() {
            return turns.size();
        }

        private synchronized boolean isExpired(Instant now) {
            return Duration.between(lastAccess, now).compareTo(SESSION_TTL) > 0;
        }
    }
}
