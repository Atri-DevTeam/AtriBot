package top.yzljc.atribot.platform.qq;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName MessageReference
 * @Created_at 2026/09/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
public record MessageReference(
        @Nullable String msgIdx,
        @Nullable String preview,
        int messageType,
        @Nullable String content
) {
    @Nullable
    public String getRefContent() {
        if (this.content == null) {
            return null;
        }
        String delimiter = "[关联消息]";

        int index = this.content.indexOf(delimiter);

        // 没有关联消息，就返回原内容
        if (index == -1) {
            return this.content;
        }

        return this.content.substring(0, index).trim();
    }

    public List<String> geRelatedMessages() {
        if (this.content == null) {
            return new ArrayList<>();
        }
        String delimiter = "[关联消息]";

        int first = this.content.indexOf(delimiter);
        if (first == -1) {
            return new ArrayList<>();
        }

        // 第一个 [关联消息] 前面的不算
        String related = this.content.substring(first + delimiter.length());

        return Arrays.stream(related.split("\\Q" + delimiter + "\\E"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
