package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.media.ImageType;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupChat
 * @Created_at 2026/06/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public final class GroupChat {

    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, String text) {
        return service().sendActiveGroupTextMessage(groupOpenId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, Markdown markdown) {
        return service().sendActiveGroupMarkdownMessage(groupOpenId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, ImageType type, String value) {
        return service().sendActiveGroupImageMessage(groupOpenId, type, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String groupOpenId, Markdown markdown, Object keyboard) {
        return service().sendActiveGroupMarkdownMessage(groupOpenId, markdown, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, String replyText) {
        return service().replyGroupTextMessage(groupOpenId, msgId, replyText);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown) {
        return service().replyGroupMarkdownMessage(groupOpenId, userOpenId, msgId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String userOpenId, String msgId, Markdown markdown, Object keyboard) {
        return service().replyGroupMarkdownMessage(groupOpenId, userOpenId, msgId, markdown, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, ImageType type, String value) {
        return service().replyGroupImageMessage(groupOpenId, msgId, type, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String groupOpenId, String msgId, int type, String value) {
        return service().replyGroupFileMessage(groupOpenId, msgId, type, value);
    }

    public static void recallMessage(String groupOpenId, String messageId) {
        service().recallGroupMessage(groupOpenId, messageId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String welcomeMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown) {
        return service().sendGroupWelcome(groupOpenId, memberOpenId, eventId,markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String welcomeMessage(String groupOpenId, String memberOpenId, String eventId, Markdown markdown, Object buttons) {
        return service().sendGroupWelcome(groupOpenId, memberOpenId, eventId,markdown, buttons);
    }
}
