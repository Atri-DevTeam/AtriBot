package top.yzljc.atribot.chat.official.thread;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.utils.JsonPayload;

import java.util.Map;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName Thread
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 * @Description 频道帖子
 */
@Getter
@AllArgsConstructor
public class GuildThread implements JsonPayload {
    private final String title;
    private final String content;
    private final Format format;

    /**
     * 创建 JSON 格式的帖子，构造时保存富文本正文的序列化快照。
     *
     * @param title 帖子标题
     * @param content 富文本正文
     * @throws NullPointerException 标题或正文为 {@code null}
     */
    public GuildThread(String title, RichText content) {
        this(Objects.requireNonNull(title, "title"),
                Objects.requireNonNull(content, "content").toJson(), Format.FORMAT_JSON);
    }

    @Override
    public Object toObject() {
        return Map.of("title", title, "content", content, "format", format.getValue());
    }
}
