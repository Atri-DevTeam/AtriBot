package top.yzljc.atribot.function.utils.personal;

import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.EmailMessageEvent;

/**
 * @Author YZ_Ljc_
 * @ClassName EmailNotify
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat.personal
 */
public class EmailNotify implements Listener {

    private static final String OWNER = "3199590352";

    @EventHandler
    public void onEmailReceived(EmailMessageEvent event) {
        String email = """
                主题: %s
                发件人: %s
                收件人: %s
                接收时间: %s
                抄送: %s
                密信: %s
                内容: %s
                附件数量: %s
                """.formatted(
                event.getSubject(),
                event.getAuthors().toString(),
                event.getToRecipients().toString(),
                event.getReceivedDate().toString(),
                event.getCcRecipients(),
                event.getBccRecipients(),
                event.getContentSummary(),
                event.getAttachmentFileNames().size());

        PrivateMessage.chatMessage(OWNER, email);
    }
}