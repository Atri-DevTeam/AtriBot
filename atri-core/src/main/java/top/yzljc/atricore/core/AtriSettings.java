package top.yzljc.atricore.core;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 框架运行配置，由 {@link CoreConfig} 从 YAML 文件加载
 *
 * <p>通过 {@code CoreConfig.getInstance().getSettings()} 获取当前配置对象
 * 字段注释中的配置键对应 {@code atri-core.yml} 的层级路径
 *
 * <p>Setter 支持链式调用，仅修改内存对象，不写回配置文件，也不重建已启动的服务
 * 连接、监听端口及线程队列配置修改后需要重启
 * 线程与队列参数可由对应的 {@code atribot.*} JVM 系统属性覆盖
 *
 * @Author YZ_Ljc_
 * @ClassName AtriSettings
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
@Getter
@Setter
@Accessors(chain = true)
public final class AtriSettings {
    /**
     * 共享 HTTP 服务监听端口，供平台回调与运行服务路由使用，对应 {@code listen-port}
     */
    private int listenPort = 1234;
    /**
     * QQ 官方机器人启用状态，对应 {@code qq.enabled}
     */
    private boolean officialBotEnabled;
    /**
     * QQ 开放平台应用 ID，对应 {@code qq.app-id}
     */
    private String qqAppId = "";
    /**
     * QQ 开放平台应用密钥，用于获取访问令牌及 Webhook 验签，对应 {@code qq.client-secret}
     */
    private String qqClientSecret = "";
    /**
     * QQ 事件接入方式，支持 {@code ws} 与 {@code webhook}，对应 {@code qq.connection-mode}
     */
    private String qqConnectionMode = "ws";
    /**
     * QQ Webhook 在共享 HTTP 服务中的回调路径，对应 {@code qq.webhook-path}
     */
    private String qqWebhookPath = "/qq/webhook";
    /**
     * QQ 开放平台 API 基础地址，对应 {@code qq.api-base-url}
     */
    private String qqApiBaseUrl = "https://api.bot.qq.com";
    /**
     * QQ 调试群 OpenID，亦作为备用分片上传的目标群，对应 {@code qq.debug-group-openId}
     */
    private String debugGroupOpenId = "";
    /**
     * NapCat HTTP API 地址，对应 {@code napcat.server-url}
     */
    private String napcatServerUrl = "http://127.0.0.1:3000";
    /**
     * NapCat 接入启用状态，对应 {@code napcat.enabled}
     */
    private boolean napcatEnabled;
    /**
     * 调用 NapCat HTTP API 使用的 Bearer 令牌，对应 {@code napcat.token}
     */
    private String authToken = "";
    /**
     * NapCat 登录账号的 QQ 号，用于识别自身消息及过滤对自身的戳一戳，对应 {@code napcat.bot-uin}
     */
    private String napcatBotUin = "";
    /**
     * NapCat 调试消息接收群号，调试模式下群列表仅返回此群，对应 {@code napcat.debug-group-uin}
     */
    private String napcatDebugGroupUin = "";
    /**
     * NapCat 群聊管理员 QQ 号列表，仅参与群聊权限判断，对应 {@code napcat.admin-uins}
     */
    private List<String> napcatAdminUins = List.of();
    /**
     * 允许分发 NapCat 群消息撤回事件的群号列表，不限制私聊撤回事件，对应 {@code napcat.message-spy-groups}
     */
    private List<String> napcatMessageSpyGroups = List.of();
    /**
     * 框架调试模式，启用后 NapCat 群列表查询仅返回调试群，对应 {@code debug-mode}
     */
    private boolean debugMode;
    /**
     * 文本指令前缀，对应 {@code command-prefix}
     */
    private String commandPrefix = "/";
    /**
     * 共享工作执行器并发任务上限，{@code 0} 按 CPU 数量自动计算为 16～128，对应 {@code thread.max-concurrency}
     */
    private int threadMaxConcurrency;
    /**
     * 共享工作执行器的等待队列容量，单位为任务数，对应 {@code thread.queue-capacity}
     */
    private int threadQueueCapacity = 4096;
    /**
     * 尚未转交工作执行器的延迟任务数上限，对应 {@code thread.schedule-capacity}
     */
    private int threadScheduleCapacity = 4096;
    /**
     * QQ Webhook 事件队列容量，对应 {@code qq.webhook-queue-capacity}
     */
    private int qqWebhookQueueCapacity = 1024;
    /**
     * NapCat 已接收但尚未处理完成的事件数上限，对应 {@code napcat.queue-capacity}
     */
    private int napcatQueueCapacity = 1024;
    /**
     * NapCat 未处理完成事件的请求正文累计字节数上限，默认 16 MiB，对应 {@code napcat.queue-bytes}
     */
    private long napcatQueueBytes = 16L * 1024 * 1024;
    /**
     * NapCat 事件分发工作线程数，允许 1～64，同一会话仍按顺序处理，对应 {@code napcat.workers}
     */
    private int napcatWorkers = 8;

    /**
     * 设置 NapCat 群聊管理员列表并保存不可修改的副本
     *
     * @param users 管理员 QQ 号列表，{@code null} 按空列表处理
     * @return 当前配置对象
     * @throws NullPointerException 列表包含 {@code null} 元素
     */
    public AtriSettings setNapcatAdminUins(List<String> users) {
        napcatAdminUins = users == null ? List.of() : List.copyOf(users);
        return this;
    }

    /**
     * 设置群消息撤回事件的监听群列表并保存不可修改的副本
     *
     * @param groups 群号列表，{@code null} 按空列表处理
     * @return 当前配置对象
     * @throws NullPointerException 列表包含 {@code null} 元素
     */
    public AtriSettings setNapcatMessageSpyGroups(List<String> groups) {
        napcatMessageSpyGroups = groups == null ? List.of() : List.copyOf(groups);
        return this;
    }
}
