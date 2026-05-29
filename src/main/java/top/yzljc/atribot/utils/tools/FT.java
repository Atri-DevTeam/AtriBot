package top.yzljc.atribot.utils.tools;

import org.apache.commons.text.StringEscapeUtils;

public class FT {

    // 将文本中的HTML实体转换回原始字符
    public static String unescape(String text) {
        if (text == null) return null;
        return StringEscapeUtils.unescapeHtml4(text);
    }
}
