package top.yzljc.sakuraba_ema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelCalls
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema
 * @Description 取得配置中的频道客户端，供业务方法内部执行请求
 */
@Slf4j
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

    /**
     * 执行业务查询并提取数据，保留分页游标及交互流程状态
     *
     * @param request CLI 查询
     * @return 成功响应中的数据；调用失败或响应没有数据时为 null
     */
    public static JsonNode query(Supplier<ChannelCliResult> request) {
        ChannelCliResult result = invoke(request);
        if (result == null || !result.success()) return null;
        JsonNode data = result.getData();
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    /**
     * 提取系统命令结果，兼容 schema 等命令直接返回 JSON 对象或数组
     *
     * @param request CLI 系统查询
     * @return 响应数据或原始 JSON，调用失败时为 null
     */
    public static JsonNode systemQuery(Supplier<ChannelCliResult> request) {
        ChannelCliResult result = invoke(request);
        if (result == null || !result.success()) return null;
        JsonNode response = result.response();
        JsonNode data = response.has("data") ? response.path("data") : response;
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    /**
     * 执行业务操作
     *
     * @param request CLI 操作
     * @return CLI 确认操作成功时为 true
     */
    public static boolean action(Supplier<ChannelCliResult> request) {
        ChannelCliResult result = invoke(request);
        return result != null && result.success();
    }

    /**
     * 执行返回纯文本的系统查询
     *
     * @param request CLI 查询
     * @return 标准输出文本，失败时为 null
     */
    public static String text(Supplier<ChannelCliResult> request) {
        ChannelCliResult result = invoke(request);
        return result != null && result.success() ? result.stdout() : null;
    }

    private static ChannelCliResult invoke(Supplier<ChannelCliResult> request) {
        try {
            return request.get();
        } catch (ChannelCliException | IllegalArgumentException e) {
            log.warn("QQ 频道调用失败: {}", e.getMessage());
            return null;
        }
    }
}
