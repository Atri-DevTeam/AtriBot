package top.yzljc.atribot.chat.official.thread;

/**
 * @Author YZ_Ljc_
 * @ClassName ReplyInfo
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public record ReplyInfo(
        String threadId,
        String postId,
        String replyId,
        String content,
        String dateTime
) {

    public RichText getContentAsRichText() {
        return RichText.fromJson(content);
    }
}
