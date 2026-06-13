package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.Atri;

/**
 * @Author YZ_Ljc_
 * @ClassName C2CChat
 * @Created_at 2026/06/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public final class C2CChat {

    private static ChatService service() {
        return Atri.getInstance().getChatService();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, String text) {
        return service().sendActivePrivateTextMessage(openId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, Markdown markdown) {
        return service().sendActivePrivateMarkdownMessage(openId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, ImageType type, String value) {
        return service().sendActivePrivateImageMessage(openId, type, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String sendMessage(String openId, Ark ark) {
        return service().sendActivePrivateArkMessage(openId, ark);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, String replyText) {
        return service().replyPrivateTextMessage(openId, msgId, replyText);
    }

    public static String sendMessage(String openId, Markdown markdown, Object keyboard) {
        return service().sendActivePrivateMarkdownMessage(openId, markdown, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, Markdown markdown) {
        return service().replyPrivateMarkdownMessage(openId, msgId, markdown);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, Markdown markdown, Object keyboard) {
        return service().replyPrivateMarkdownMessage(openId, msgId, markdown, keyboard);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, ImageType type, String value) {
        return service().replyPrivateImageMessage(openId, msgId, type, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String replyMessage(String openId, String msgId, Ark ark) {
        return service().replyPrivateArkMessage(openId, msgId, ark);
    }

    public static void recallMessage(String openId, String messageId) {
        service().recallPrivateMessage(openId, messageId);
    }
}
