package top.yzljc.sakuraba_ema;

import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.nio.file.Path;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelSystem
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema
 * @Description 频道 CLI 版本、登录与命令结构查询
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelSystem {

    private ChannelSystem() {
    }

    /**
     * 查询 CLI 版本
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult version() {
        return ChannelCalls.client().system().version();
    }

    /**
     * 检查 CLI 运行环境
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult doctor() {
        return ChannelCalls.client().system().doctor();
    }

    /**
     * 获取第二账号登录二维码
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult login() {
        return ChannelCalls.client().system().login();
    }

    /**
     * 获取第二账号登录二维码，可指定图片保存位置
     *
     * @param qrcodePath 二维码文件路径，null 时使用 CLI 默认位置
     * @param forceRelogin 是否添加 --yes 强制重新登录
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult login(Path qrcodePath, boolean forceRelogin) {
        return ChannelCalls.client().system().login(qrcodePath, forceRelogin);
    }

    /**
     * 轮询扫码登录结果
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult pollToken() {
        return ChannelCalls.client().system().pollToken();
    }

    /**
     * 查询第二账号登录状态
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult loginStatus() {
        return ChannelCalls.client().system().loginStatus();
    }

    /**
     * 查询 CLI 全部命令目录
     *
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult schema() {
        return ChannelCalls.client().system().schema();
    }

    /**
     * 查询指定命令的参数结构
     *
     * @param commandPath 命令路径，如 feed.publish-feed、manage.kick-guild-member
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult schema(String commandPath) {
        return ChannelCalls.client().system().schema(commandPath);
    }

    /**
     * 按关键字搜索命令结构
     *
     * @param keyword 非空搜索关键字
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchSchema(String keyword) {
        return ChannelCalls.client().system().searchSchema(keyword);
    }
}
