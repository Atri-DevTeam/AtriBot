package top.yzljc.atribot.chat.official.thread;

/**
 * @Author YZ_Ljc_
 * @ClassName PostInfo
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public record PostInfo(
        String threadId,
        String postId,
        String content,
        String dateTime,
        String threadAuthorId
) {

    public RichText getContentAsRichText() {
        return RichText.fromJson(content);
    }
}
