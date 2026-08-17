package top.yzljc.sakuraba_ema.guild.impl;

/**
 * Options shared by all tencent-channel-cli invocations.
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
