package top.yzljc.atribot.service.textreview;

/**
 * @Author YZ_Ljc_
 * @ClassName TextRange
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @Description 原文 UTF-16 下标区间，包含 start，不包含 end
 */
record TextRange(int start, int end) {
    TextRange {
        if (start < 0 || end <= start) throw new IllegalArgumentException("无效文本区间");
    }
}
