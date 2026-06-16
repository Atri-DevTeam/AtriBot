package top.yzljc.atribot.function.general.impl;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.service.AiService;

@Slf4j
public class AtriNewsSummarizer {

    private static final AiService aiService = Atri.getInstance().getAiService();

    private static final String SYSTEM_PROMPT =
            "你是亚托莉（ATRI），一名高性能机器人。你的表达风格简洁、理性、有条理。" +
                    "允许轻微体现角色语气，但必须以“信息准确、结构清晰”为第一优先级。" +
                    "禁止冗余表达、禁止自我发挥剧情、禁止无意义修饰。" +
                    "你的任务是生成高密度新闻摘要，而不是闲聊。";

    public static String summarize(String title, String articleText) {
        if (aiService == null) {
            return "哎呀，亚托莉的 AI 核心还没有接入呢，没法帮你总结了！";
        }

        if (articleText.isEmpty()) {
            return "呜……亚托莉没有在这个链接里找到任何有用的文字信息，我是高性能的嘛，肯定不是我的问题！";
        }

        String userPrompt =
                "请对以下 Minecraft 新闻进行结构化摘要。\n\n" +

                        "【输出格式（必须严格遵守）】\n" +
                        "1. 第一条消息：用一句话概括核心内容，后面要跟着[BR]换行\n" +
                        "2. 后续内容：按编号或者“-”开头列出具体信息\n\n" +

                        "【内容分类要求（必须遵守）】\n" +
                        "- 根据内容自动分组，而不是强制固定分类\n" +
                        "- 常见分组包括：新增内容、机制改动、活动信息、修复优化、技术更新\n" +
                        "- 如果某一类不存在，则跳过该分类\n" +
                        "- 如果存在“修复/优化/技术更新”，必须单独列出，不得混入其他类别\n" +
                        "- 禁止编造不存在的内容\n\n" +

                        "【硬性约束】\n" +
                        "- 每一条必须是具体信息（新增内容、改动、活动细节等）\n" +
                        "- 禁止空话、总结性废话、解释性语句\n" +
                        "- 禁止重复信息\n" +
                        "- 禁止使用括号、表情、语气词\n" +
                        "- 每条必须编号\n\n" +

                        "【消息拆分规则（强制）】\n" +
                        "- 每条内容中只概括一项具体信息，禁止合并\n" +
                        "- 超过长度限制必须拆分为新消息，按顺序连续输出，不得跳号或重排\n" +
                        "- 优先按“内容分类”组织消息结构\n" +
                        "- 每个分类标题用$小标题$包围，标题独占一行，标题后紧跟一个[BR]\n" +
                        "- 标题下的每条信息以\"  - \"开头（注意空格），每条信息末尾跟一个[BR]\n" +
                        "- 不同分类之间连续输出两个[BR][BR]\n" +
                        "- 禁止将全部内容集中在一条消息中\n" +
                        "- 必须使用[BR]作为唯一换行符，禁止输出真正的换行符\\n\n\n" +
                        "示例正文文段：$新增内容$[BR]  - 更新了草方块[BR]  - 移除了Him[BR][BR]$技术更新$[BR]  - 修复了编号为MC-3048558的问题[BR]" +

                        "【技术字段规则】\n" +
                        "- 所有技术字段必须保持原样，不得改写\n" +
                        "- 技术字段包括：版本号、数字、英文专有名词、标识符、参数值\n" +
                        "- 示例：1.21.6、103.0、Java Edition、data pack 必须原样输出\n" +
                        "- 禁止将数字转为中文（如“一零三点零”）\n" +
                        "- 普通描述性内容使用中文，但技术字段保持原样\n\n" +

                        "新闻标题：" + title + "\n\n" +
                        "新闻内容：\n" + articleText;

        try {
            return aiService.askWithSystemPrompt(userPrompt, SYSTEM_PROMPT);
        } catch (Exception e) {
            log.error("调用 AI 进行新闻总结失败: ", e);
            return "亚托莉的脑回路好像卡住了……总结新闻失败了，请稍后再试呀！我是高性能的嘛！";
        }
    }
}