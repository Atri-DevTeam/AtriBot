package top.yzljc.atribot.test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.tasks.MinecraftNews;
import top.yzljc.atribot.function.impl.ArticleScraper;
import top.yzljc.atribot.function.impl.AtriNewsSummarizer;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.utils.ForumCode;

import java.net.URI;
import java.util.*;

/** 测试类 */
public class MinecraftNewsDebug implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MinecraftNewsDebug.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof ConsoleCommandSender console) {
            sender.sendMessage("[!] 调用启用！");
            ThreadManager.execute(() -> runDebug(null));
            return true;
        }

        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!nc.hasPermission()) {
            nc.sendMessage("无权限");
            return true;
        }

        String specifiedUrl = null;
        for (int i = 0; i < args.length; i++) {
            if (!"-url".equalsIgnoreCase(args[i])) continue;
            if (i + 1 >= args.length || args[i + 1].isBlank()) {
                nc.sendMessage("用法: /" + label + " -url <文章链接>");
                return true;
            }
            specifiedUrl = args[i + 1].trim();
            break;
        }

        if (specifiedUrl != null && !isHttpUrl(specifiedUrl)) {
            nc.sendMessage("-url 仅支持 http:// 或 https:// 链接");
            return true;
        }

        String targetUrl = specifiedUrl;
        ThreadManager.execute(() -> runDebug(targetUrl));
        nc.sendMessage(targetUrl == null
                ? "[DEBUG] MinecraftNews 链路测试已启动，结果仅发送到 debug 群..."
                : "[DEBUG] MinecraftNews 指定链接测试已启动，结果仅发送到 debug 群...");
        return true;
    }

    private static void runDebug(String specifiedUrl) {
        String debugGroup = Config.getInstance().getNapcatDebugGroupUin();

        try {
            MinecraftNews.UnifiedArticle article;
            if (specifiedUrl != null) {
                GroupMessage.chatMessage(debugGroup, "[DEBUG] MinecraftNews 链路测试开始，正在读取指定文章...");
                article = fetchSpecifiedArticle(specifiedUrl);
                log.info("[DEBUG] 使用指定文章 [{}] {}: {}", article.tag, article.title, article.url);
                GroupMessage.chatMessage(debugGroup,
                        "[DEBUG] 使用指定文章\n"
                                + "[" + article.tag + "] " + article.title + "\n"
                                + "URL: " + article.url);
            } else {
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

                int index = new Random().nextInt(allArticles.size());
                article = allArticles.get(index);

                log.info("[DEBUG] 随机选中 ({} / {}) [{}] {}: {}",
                        index + 1, allArticles.size(), article.tag, article.title, article.url);
                GroupMessage.chatMessage(debugGroup,
                        "[DEBUG] 随机选中文章 (" + (index + 1) + "/" + allArticles.size() + ")\n"
                                + "[" + article.tag + "] " + article.title + "\n"
                                + "URL: " + article.url);
            }

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

//            log.warn(data.url());
            GroupMessage.chatMessage(debugGroup, ImageComponent.imageOf(data.url()));

            Markdown forumsMarkdown = TC.md(
                    "Minecraft官网发布了新的文章，点击图片查看详细！\n\n"
                            + "![MC #" + data.width() + "px #" + data.height() + "px](" + data.url() + ")"
            );

            String markdown = "**Minecraft官方发布了新的文章，点击图片查看详情！**\n\n" + "> 时间: " + "2026-3-5 19:00" + "\n\n" + "![MC #" + data.width() + "px #" + data.height() + "px](" + data.url() + ")\n\n" +
                    "> " + Markdown.enterCommand("/tasks disable mc_news", "关闭此类推送");

            GroupChat.sendMessage(Config.getInstance().getDebugGroupOpenId(), TC.md(markdown));

            ChannelPosts.sendMessage(ForumCode.GUILD_ID, ForumCode.MINECRAFT_NEWS.getChannelId(), "[动态] " + article.title, forumsMarkdown);
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

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static MinecraftNews.UnifiedArticle fetchSpecifiedArticle(String url) throws Exception {
        Document document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .timeout(20_000)
                .get();

        MinecraftNews.UnifiedArticle article = new MinecraftNews.UnifiedArticle();
        article.id = url;
        article.url = document.location().isBlank() ? url : document.location();
        article.title = firstNonBlank(
                metaContent(document, "meta[property=og:title]"),
                metaContent(document, "meta[name=twitter:title]"),
                document.title(),
                url
        );
        article.description = firstNonBlank(
                metaContent(document, "meta[property=og:description]"),
                metaContent(document, "meta[name=description]"),
                ""
        );
        article.author = firstNonBlank(
                metaContent(document, "meta[name=author]"),
                metaContent(document, "meta[property=article:author]"),
                "Minecraft"
        );
        article.imageUrl = firstNonBlank(
                absoluteMetaContent(document, "meta[property=og:image]"),
                absoluteMetaContent(document, "meta[name=twitter:image]"),
                ""
        );
        article.dateDisplay = firstNonBlank(
                metaContent(document, "meta[property=article:published_time]"),
                metaContent(document, "meta[name=date]"),
                elementAttribute(document, "time[datetime]", "datetime"),
                "未知时间"
        );
        article.tag = "指定链接";
        return article;
    }

    private static String metaContent(Document document, String selector) {
        return elementAttribute(document, selector, "content");
    }

    private static String absoluteMetaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        if (element == null) return "";
        String absolute = element.absUrl("content");
        return absolute.isBlank() ? element.attr("content").trim() : absolute;
    }

    private static String elementAttribute(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.attr(attribute).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }
}
