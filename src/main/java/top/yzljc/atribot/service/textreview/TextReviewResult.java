package top.yzljc.atribot.service.textreview;

import java.util.List;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName TextReviewResult
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @param content 审核后的完整文本，未命中部分保持原样
 * @param replacements 按原文位置排序的实际替换记录，不包含未变化的片段
 */
public record TextReviewResult(String content, List<Replacement> replacements) {
    public TextReviewResult {
        Objects.requireNonNull(content, "content");
        replacements = List.copyOf(replacements);
    }

    /**
     * @Author YZ_Ljc_
     * @ClassName Replacement
     * @Created_at 2026/09/23
     * @Project AtriMeow
     * @Package top.yzljc.atribot.service.textreview
     * @param start 原文 UTF-16 起始下标，从 0 开始，包含该位置
     * @param end 原文 UTF-16 结束下标，不包含该位置
     * @param original 该区间内的原始片段，不包含周围未修改的文本
     * @param replacement 实际替换文本，星号数量与原始片段的 Unicode 码点数相同
     */
    public record Replacement(int start, int end, String original, String replacement) {}
}
