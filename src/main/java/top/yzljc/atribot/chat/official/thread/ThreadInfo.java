package top.yzljc.atribot.chat.official.thread;

/**
 * @Author YZ_Ljc_
 * @ClassName ThreadInfo
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
public record ThreadInfo(
        String threadId,
        String title,
        String content,
        String dateTime
) {

    public RichText getTitleAsRichText() {
        return RichText.fromJson(title);
    }

    public RichText getContentAsRichText() {
        return RichText.fromJson(content);
    }
}
