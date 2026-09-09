package top.yzljc.sakuraba_ema.guild.impl;

/**
 * CLI 执行选项，所有同步与异步入口共用
 *
 * @param yes 是否传入 --yes，沿用 CLI 对需要确认的操作的处理
 * @param dryRun 是否传入 --dry-run，仅预演命令参数
 */
public record ChannelCliOptions(boolean yes, boolean dryRun) {

    public static final ChannelCliOptions DEFAULT = new ChannelCliOptions(false, false);
    public static final ChannelCliOptions CONFIRMED = new ChannelCliOptions(true, false);
    public static final ChannelCliOptions DRY_RUN = new ChannelCliOptions(false, true);

    public ChannelCliOptions withYes(boolean value) {
        return new ChannelCliOptions(value, dryRun);
    }

    public ChannelCliOptions withDryRun(boolean value) {
        return new ChannelCliOptions(yes, value);
    }
}
