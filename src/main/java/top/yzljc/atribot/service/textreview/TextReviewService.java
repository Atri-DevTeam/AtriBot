package top.yzljc.atribot.service.textreview;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.ai.AiProperties;
import top.yzljc.atribot.service.ai.AiProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName TextReviewService
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @Description 独立的主动推送文本审查接口，不注册事件或调用发送、撤回接口
 */
public final class TextReviewService {
    private static volatile TextReviewService defaultService;
    private final LocalWordMatcher words;
    private final Function<String, List<TextRange>> aiReview;

    TextReviewService(LocalWordMatcher words, Function<String, List<TextRange>> aiReview) {
        this.words = Objects.requireNonNull(words);
        this.aiReview = Objects.requireNonNull(aiReview);
    }

    /**
     * @param content 待推送的原始文本，不允许为 null；空白文本原样返回
     * @param allowedDomains 允许保留链接的完整域名或 HTTP(S) URL，只比较域名，子域名须单独列出；null 或空集合表示屏蔽全部链接
     * @return 违规片段和非白名单链接按原文 Unicode 码点数替换为等量的 *，未命中字符保持不变
     * @throws IllegalArgumentException 白名单包含空值或无法解析的域名
     * @throws TextReviewException 词库加载失败、AI 不可用或审核结果无效，不返回未经完整审核的文本
     */
    public static String review(String content, Collection<String> allowedDomains) {
        return reviewDetailed(content, allowedDomains).content();
    }

    /**
     * @param content 待推送的原始文本，不允许为 null
     * @return 两轮审查后的文本，所有识别到的链接均按 Unicode 码点数替换为等量的 *
     * @throws TextReviewException 词库加载失败、AI 不可用或审核结果无效
     */
    public static String review(String content) {
        return review(content, List.of());
    }

    /**
     * @param content 待审核原文，不允许为 null；空白文本原样返回且无替换记录
     * @param allowedDomains 完整域名白名单，子域名须单独列出；null 或空集合表示屏蔽全部链接
     * @return 审核后的文本及实际替换片段，片段下标始终对应本次传入的原文
     * @throws IllegalArgumentException 白名单包含空值或无法解析的域名
     * @throws TextReviewException 规则加载失败、AI 不可用或审核结果无效
     */
    public static TextReviewResult reviewDetailed(String content, Collection<String> allowedDomains) {
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) return new TextReviewResult(content, List.of());
        return getDefaultService().sanitizeDetailed(content, allowedDomains);
    }

    /**
     * @param content 待审核原文，不允许为 null
     * @return 审核后的文本及实际替换片段，全部识别到的链接均按 Unicode 码点数替换为等量的 *
     * @throws TextReviewException 规则加载失败、AI 不可用或审核结果无效
     */
    public static TextReviewResult reviewDetailed(String content) {
        return reviewDetailed(content, List.of());
    }

    /**
     * @param wordsFile UTF-8 词库文件，一行一个词；构造时一次读入
     * @param promptFile UTF-8 审核提示词文件，构造时一次读入
     * @param properties 要使用的模型配置，默认入口固定使用 ai.default
     * @param timeout AI 请求超时，必须大于 0
     * @return 可重复调用 sanitize 的独立审查实例
     * @throws IllegalArgumentException timeout 不大于 0
     * @throws TextReviewException 本地规则文件无法加载
     */
    public static TextReviewService create(Path wordsFile, Path promptFile, AiProperties properties, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("审核超时必须大于 0");
        try {
            String prompt = Files.readString(promptFile, StandardCharsets.UTF_8);
            if (prompt.isBlank()) throw new TextReviewException("审核提示词为空");
            return new TextReviewService(new LocalWordMatcher(wordsFile), new AiTextReviewClient(properties, timeout, prompt));
        } catch (IOException e) {
            throw new TextReviewException("无法加载本地文本审核规则，请检查 " + wordsFile.toAbsolutePath().normalize()
                    + " 和 " + promptFile.toAbsolutePath().normalize(), e);
        }
    }

    /**
     * @param content 待审查原文，不允许为 null；保留原有空格、换行及格式
     * @param allowedDomains 链接域名白名单，精确匹配且不自动允许子域名；null 或空集合表示全部屏蔽
     * @return 违规片段和非白名单链接按原文 Unicode 码点数替换为等量的 *，白名单链接原样保留
     * @throws IllegalArgumentException 白名单包含空值或无法解析的域名
     * @throws TextReviewException AI 未完成审核或返回无法定位的片段
     */
    public String sanitize(String content, Collection<String> allowedDomains) {
        return sanitizeDetailed(content, allowedDomains).content();
    }

    /**
     * @param content 待审核原文，不允许为 null；未命中的字符保持不变
     * @param allowedDomains 完整域名白名单，null 或空集合表示屏蔽全部链接
     * @return 审核后的文本及按原文位置排序的实际替换记录；白名单链接不产生替换记录
     * @throws IllegalArgumentException 白名单包含空值或无法解析的域名
     * @throws TextReviewException AI 未完成审核或返回无法定位的片段
     */
    public TextReviewResult sanitizeDetailed(String content, Collection<String> allowedDomains) {
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) return new TextReviewResult(content, List.of());
        List<LinkMatcher.Link> links = LinkMatcher.find(content, allowedDomains);
        List<TextRange> ranges = new ArrayList<>(words.find(content));
        List<TextRange> aiRanges = aiReview.apply(content);
        if (aiRanges == null) throw new TextReviewException("AI 未返回审核结果");
        ranges.addAll(aiRanges);
        boolean[] masked = new boolean[content.length()];
        for (TextRange range : ranges) {
            if (range == null || range.end() > content.length()) throw new TextReviewException("审核片段超出原文范围");
            Arrays.fill(masked, range.start(), range.end(), true);
        }
        StringBuilder result = new StringBuilder(content.length());
        List<TextReviewResult.Replacement> replacements = new ArrayList<>();
        int index = 0;
        int linkIndex = 0;
        while (index < content.length()) {
            LinkMatcher.Link link = linkIndex < links.size() ? links.get(linkIndex) : null;
            if (link != null && index == link.start()) {
                if (link.allowed()) result.append(content, link.start(), link.end());
                else {
                    String original = content.substring(link.start(), link.end());
                    String replacement = "*".repeat(original.codePointCount(0, original.length()));
                    result.append(replacement);
                    replacements.add(new TextReviewResult.Replacement(link.start(), link.end(), original, replacement));
                }
                index = link.end();
                linkIndex++;
            } else if (masked[index]) {
                int start = index;
                do {
                    index++;
                } while (index < content.length() && masked[index] && (link == null || index < link.start()));
                String original = content.substring(start, index);
                String replacement = "*".repeat(original.codePointCount(0, original.length()));
                result.append(replacement);
                if (!original.equals(replacement)) {
                    replacements.add(new TextReviewResult.Replacement(start, index, original, replacement));
                }
            } else {
                result.append(content.charAt(index++));
            }
        }
        return new TextReviewResult(result.toString(), replacements);
    }

    private static TextReviewService getDefaultService() {
        TextReviewService service = defaultService;
        if (service == null) {
            synchronized (TextReviewService.class) {
                service = defaultService;
                if (service == null) {
                    Path directory = Path.of("data", "text-review");
                    service = create(directory.resolve("words.txt"), directory.resolve("prompt.txt"),
                            Config.getInstance().getAiPropertiesMap().get(AiProvider.DEFAULT), Duration.ofSeconds(10));
                    defaultService = service;
                }
            }
        }
        return service;
    }
}
