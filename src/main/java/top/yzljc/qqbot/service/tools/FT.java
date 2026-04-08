package top.yzljc.qqbot.service.tools;

import org.apache.commons.text.StringEscapeUtils;

public class FT {
    /**
     * @Author YZ_Ljc_
     * @description 将文本中的HTML实体转换回原始字符
     * @param text 需要转换的文本
     * @return 转换后的文本
     */
    public static String unescape(String text) {
        if (text == null) return null;
        return StringEscapeUtils.unescapeHtml4(text);
    }
}
