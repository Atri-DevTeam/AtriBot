package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;

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
    public static String sendMessage(String groupOpenId, Ark ark) {
        return service().sendActiveGroupArkMessage(groupOpenId, ark);
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
    public static String replyMessage(String groupOpenId, String msgId, Ark ark) {
        return service().replyGroupArkMessage(groupOpenId, msgId, ark);
    }

    public static void recallMessage(String groupOpenId, String messageId) {
        service().recallGroupMessage(groupOpenId, messageId);
    }
}
