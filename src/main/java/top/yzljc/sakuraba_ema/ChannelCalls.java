package top.yzljc.sakuraba_ema;

import top.yzljc.atribot.Atri;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelCalls
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema
 * @Description 取得配置中的频道客户端，供业务方法内部执行请求
 */
public final class ChannelCalls {
    private ChannelCalls() {
    }

    /**
     * 复用 Atri 管理的客户端
     *
     * @return 已初始化的频道客户端
     * @throws ChannelCliException 客户端尚未初始化
     */
    public static ChannelCliClient client() {
        Atri atri = Atri.getInstance();
        if (atri == null || atri.getTencentChannelCliClient() == null) {
            throw new ChannelCliException("腾讯频道 CLI 客户端尚未初始化");
        }
        return atri.getTencentChannelCliClient();
    }
}
