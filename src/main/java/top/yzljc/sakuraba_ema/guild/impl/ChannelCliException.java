package top.yzljc.sakuraba_ema.guild.impl;

public class ChannelCliException extends RuntimeException {

    public ChannelCliException(String message) {
        super(message);
    }

    public ChannelCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
