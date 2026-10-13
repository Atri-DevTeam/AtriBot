package top.yzljc.sakuraba_ema;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.internal.SystemTools;

import java.nio.file.Path;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelSystem
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema
 * @Description 频道 CLI 版本、登录与命令结构查询

 */
public final class ChannelSystem {

    private ChannelSystem() {
    }

    /**
     * 查询 CLI 版本
     *
     * @return 查询结果，调用失败时为 null
     */
    public static String version() {
        return ChannelCalls.text(SystemTools::version);
    }

    /**
     * 检查 CLI 运行环境
     *
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode doctor() {
        return ChannelCalls.systemQuery(SystemTools::doctor);
    }

    /**
     * 获取第二账号登录二维码
     *
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode login() {
        return ChannelCalls.systemQuery(SystemTools::login);
    }

    /**
     * 获取第二账号登录二维码，可指定图片保存位置
     *
     * @param qrcodePath 二维码文件路径，null 时使用 CLI 默认位置
     * @param forceRelogin 是否添加 --yes 强制重新登录
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode login(Path qrcodePath, boolean forceRelogin) {
        return ChannelCalls.systemQuery(() -> SystemTools.login(qrcodePath, forceRelogin));
    }

    /**
     * 轮询扫码登录结果
     *
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode pollToken() {
        return ChannelCalls.systemQuery(SystemTools::pollToken);
    }

    /**
     * 查询第二账号登录状态
     *
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode loginStatus() {
        return ChannelCalls.systemQuery(SystemTools::loginStatus);
    }

    /**
     * 查询 CLI 全部命令目录
     *
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode schema() {
        return ChannelCalls.systemQuery(SystemTools::schema);
    }

    /**
     * 查询指定命令的参数结构
     *
     * @param commandPath 命令路径，如 feed.publish-feed、manage.kick-guild-member
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode schema(String commandPath) {
        return ChannelCalls.systemQuery(() -> SystemTools.schema(commandPath));
    }

    /**
     * 按关键字搜索命令结构
     *
     * @param keyword 非空搜索关键字
     * @return 查询结果，调用失败时为 null
     */
    public static JsonNode searchSchema(String keyword) {
        return ChannelCalls.systemQuery(() -> SystemTools.searchSchema(keyword));
    }
}
