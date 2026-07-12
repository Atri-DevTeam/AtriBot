package top.yzljc.atribot.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.general.MinecraftNews;
import top.yzljc.atribot.function.general.impl.ArticleScraper;
import top.yzljc.atribot.function.general.impl.AtriNewsSummarizer;
import top.yzljc.atribot.function.general.impl.ImageDTO;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.*;

public class MinecraftNewsDebug implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MinecraftNewsDebug.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!sender.hasPermission()) {
            sender.sendMessage("无权限");
            return true;
        }

        ThreadManager.execute(MinecraftNewsDebug::runDebug);
        sender.sendMessage("[DEBUG] MinecraftNews 链路测试已启动，结果仅发送到 debug 群...");
        return true;
    }

    private static void runDebug() {
        String debugGroup = Config.getInstance().getNapcatDebugGroupUin();

        try {
            GroupMessage.chatMessage(debugGroup, "[DEBUG] MinecraftNews 链路测试开始，正在拉取文章列表...");

            List<MinecraftNews.UnifiedArticle> primaryList = MinecraftNews.fetchAndParsePrimary();
            List<MinecraftNews.UnifiedArticle> secondaryList = MinecraftNews.fetchAndParseSecondary();

            List<MinecraftNews.UnifiedArticle> allArticles = new ArrayList<>();
            allArticles.addAll(primaryList);
            allArticles.addAll(secondaryList);

            if (allArticles.isEmpty()) {
                log.warn("[DEBUG] 链路测试失败：没有获取到任何文章");
                GroupMessage.chatMessage(debugGroup, "[DEBUG] 链路测试失败：没有获取到任何文章");
                return;
            }

            // 随机抽取一篇
            int index = new Random().nextInt(allArticles.size());
            MinecraftNews.UnifiedArticle article = allArticles.get(index);

            log.info("[DEBUG] 随机选中 ({} / {}) [{}] {}: {}",
                    index + 1, allArticles.size(), article.tag, article.title, article.url);
            GroupMessage.chatMessage(debugGroup,
                    "[DEBUG] 随机选中文章 (" + (index + 1) + "/" + allArticles.size() + ")\n"
                            + "[" + article.tag + "] " + article.title + "\n"
                            + "URL: " + article.url);

            // ── 1. 网页抓取 ──
            log.info("[DEBUG] >>> 1. 开始提取网页纯文本: {}", article.url);
            String articleText = ArticleScraper.fetchPureText(article.url);
            if (articleText.isEmpty()) {
                log.warn("[DEBUG] >>> 网页正文为空，使用描述兜底");
                articleText = article.description;
            } else {
                log.info("[DEBUG] >>> 网页正文提取完毕，长度: {}", articleText.length());
            }

            // ── 2. AI 总结 ──
            log.info("[DEBUG] >>> 2. 开始请求 AI 进行总结...");
            String aiMessages = AtriNewsSummarizer.summarize(article.title, articleText);
            log.info("[DEBUG] >>> AI 总结完毕");

            // ── 3. 生成图片 ──
            String apiUrl = ResourcesProperties.MC_NEWS_API;
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("title", article.title);
            requestBody.put("author", article.author);
            requestBody.put("time", article.dateDisplay);
            requestBody.put("headerImageUrl", article.imageUrl);
            requestBody.put("content", aiMessages);

            ImageDTO data = PreImageGenerate.dump(apiUrl, requestBody);
            if (data.isError()) {
                String errMsg = data.errorMessage();
                log.warn("[DEBUG] >>> 新闻图片生成失败: {}", errMsg);
                GroupMessage.chatMessage(debugGroup, "[DEBUG] 链路测试失败 @ 图片生成: " + errMsg);
                return;
            }

            // ── 4. 仅推送到 debug 群 ──
            GroupMessage.chatMessage(debugGroup, data.url(), MessageUtils.ImageType.URL);
            GroupMessage.chatMessage(debugGroup,
                    "[DEBUG] 链路测试完成\n"
                            + "标题: " + article.title + "\n"
                            + "时间: " + article.dateDisplay + "\n"
                            + "原链: " + article.url);

            log.info("[DEBUG] 链路测试完成");

        } catch (Exception e) {
            log.warn("[DEBUG] 链路测试异常: {}", e.getMessage(), e);
            try {
                GroupMessage.chatMessage(debugGroup, "[DEBUG] 链路测试异常: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }
}
