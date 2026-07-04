package top.yzljc.atribot.function.napcat.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.service.ai.AiService;

/**
 * @Author YZ_Ljc_
 * @ClassName MojiraIssueSummarizer
 * @Created_at 2026/06/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class MojiraIssueSummarizer {

    private static final Logger log = LoggerFactory.getLogger(MojiraIssueSummarizer.class);
    private static final AiService aiService = Atri.getInstance().getAiService();

    private static final String SYSTEM_PROMPT =
            "你是亚托莉（ATRI），一名高性能机器人。你的表达风格简洁、理性、有条理。" +
            "你的任务是将 Minecraft Bug 追踪器（Mojira）中的英文 Bug 描述翻译为中文。" +
            "必须保持所有技术字段原样，只翻译描述性文字。" +
            "技术字段包括但不限于：版本号、坐标、英文专有名词、物品名、方块名、生物名、" +
            "命令、类名、方法名、NBT 标签、JSON 片段、代码片段。";

    public static String translate(String key, String summary, String description) {
        if (aiService == null) {
            log.debug("[Mojira AI] AiService 未初始化，返回原文");
            return description;
        }

        if (description == null || description.isEmpty()) {
            return "";
        }

        String userPrompt =
                "请将以下 Minecraft Bug 报告翻译为中文。\n\n" +
                "Bug 编号：" + key + "\n" +
                "标题：" + summary + "\n" +
                "描述：\n" + description + "\n\n" +
                "要求：\n" +
                "- 翻译为简洁的中文\n" +
                "- 版本号、物品名、方块名、生物名、命令、坐标等保留原文\n" +
                "- 保留复现步骤的完整性\n" +
                "- 去掉无意义的空行和格式噪声\n" +
                "- 直接输出翻译结果，不要加任何前缀说明";

        try {
            String result = aiService.askWithSystemPrompt(AiProvider.OTHER, userPrompt, SYSTEM_PROMPT);
            log.debug("[Mojira AI] {} 翻译完成, 原文长度={}, 译文长度={}", key, description.length(), result != null ? result.length() : 0);
            return result != null ? result : description;
        } catch (Exception e) {
            log.warn("[Mojira AI] {} 翻译失败，使用原文兜底: {}", key, e.getMessage());
            return description;
        }
    }
}
