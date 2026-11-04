package top.yzljc.atribot.function.admin.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UpdateNoticeContentTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void legacyTextDefaultsToPlainMessage() throws Exception {
        var content = json.readValue("{\"text\":\"旧通知\"}", UpdateNoticeContent.class);
        assertEquals("TEXT", content.format());
        assertTrue(content.buttons().isEmpty());
        assertEquals("亚托莉喵近期更新日志:\n> 发布时间\n\n旧通知\n\n遇到了一些问题？使用/feedback向开发者反馈！", content.render("亚托莉喵", "发布时间"));
    }

    @Test
    void layoutsRemainIndependentAndSurviveSerialization() throws Exception {
        var plain = new UpdateNoticeContent.MessageLayout("{botName} 更新于 {time}", "纯文本尾部");
        var markdown = new UpdateNoticeContent.MessageLayout("**{botName}**\n> {time}", "[反馈](https://example.com)");
        var content = new UpdateNoticeContent("正文中的 {time} 不替换", "MARKDOWN", List.of(), plain, markdown);
        var restored = json.readValue(json.writeValueAsBytes(content), UpdateNoticeContent.class);
        assertEquals(content, restored);
        assertEquals("**机器人**\n> 12:00\n\n正文中的 {time} 不替换\n\n[反馈](https://example.com)", restored.render("机器人", "12:00"));
        assertEquals("机器人 更新于 12:00\n\n正文\n\n纯文本尾部", new UpdateNoticeContent("正文", "TEXT", List.of(), plain, markdown).render("机器人", "12:00"));
    }

    @Test
    void emptyLayoutsRemoveWrappersAndRejectOversizedTemplates() {
        var empty = new UpdateNoticeContent.MessageLayout("", "");
        assertEquals("正文", new UpdateNoticeContent("正文", "TEXT", List.of(), empty, null).render("机器人", "12:00"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent.MessageLayout("x".repeat(4001), ""));
    }

    @Test
    void markdownButtonsSurviveStorageAndBuildKeyboard() throws Exception {
        var content = new UpdateNoticeContent("**更新**", "MARKDOWN", List.of(List.of(
                new UpdateNoticeContent.NoticeButton("今日问题", "/question", "COMMAND", "BLUE", false),
                new UpdateNoticeContent.NoticeButton("查看详情", "https://example.com", "LINK", "GRAY", true))));
        var restored = json.readValue(json.writeValueAsBytes(content), UpdateNoticeContent.class);
        assertEquals(content, restored);
        var md = restored.markdown("包装后的正文");
        assertEquals("包装后的正文", md.getText());
        var buttons = md.getKeyboard().getButtons().getFirst();
        assertEquals("/question", buttons.getFirst().getData());
        assertFalse(buttons.getFirst().isEnter());
        assertEquals(0, buttons.getLast().getActionType().getCode());
    }

    @Test
    void rejectInvalidFormatButtonsAndUnsafeLinks() {
        var button = new UpdateNoticeContent.NoticeButton("选择", "/question", "COMMAND", "BLUE", true);
        assertEquals(List.of(List.of(button)), new UpdateNoticeContent("消息", "TEXT", List.of(List.of(button))).buttons());
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent("消息", "RAW", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent("消息", "MARKDOWN", List.of(List.of())));
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent("消息", "MARKDOWN", java.util.Collections.nCopies(6, List.of(button))));
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent.NoticeButton("链接", "javascript:alert(1)", "LINK", "BLUE", true));
        assertThrows(IllegalArgumentException.class, () -> new UpdateNoticeContent.NoticeButton("链接", "https:///", "LINK", "BLUE", true));
    }
}
