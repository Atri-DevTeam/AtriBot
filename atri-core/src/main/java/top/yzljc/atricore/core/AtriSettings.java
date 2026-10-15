package top.yzljc.atricore.core;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 框架配置数据，由 CoreConfig 加载
 * <p>链式设置仅修改内存对象，不写回配置文件，也不重建已启动的服务
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
    private int listenPort = 1234;
    private boolean officialBotEnabled;
    private String qqAppId = "";
    private String qqClientSecret = "";
    private String qqConnectionMode = "ws";
    private String qqWebhookPath = "/qq/webhook";
    private String qqApiBaseUrl = "https://api.bot.qq.com";
    private String debugGroupOpenId = "";
    private String napcatServerUrl = "http://127.0.0.1:3000";
    private boolean napcatEnabled;
    private String authToken = "";
    private String napcatBotUin = "";
    private String napcatDebugGroupUin = "";
    private List<String> napcatAdminUins = List.of();
    private List<String> napcatMessageSpyGroups = List.of();
    private boolean debugMode;
    private String commandPrefix = "/";
    private int threadMaxConcurrency;
    private int threadQueueCapacity = 4096;
    private int threadScheduleCapacity = 4096;
    private int qqWebhookQueueCapacity = 1024;
    private int napcatQueueCapacity = 1024;
    private long napcatQueueBytes = 16L * 1024 * 1024;
    private int napcatWorkers = 8;

    public AtriSettings setNapcatAdminUins(List<String> users) {
        napcatAdminUins = users == null ? List.of() : List.copyOf(users);
        return this;
    }

    public AtriSettings setNapcatMessageSpyGroups(List<String> groups) {
        napcatMessageSpyGroups = groups == null ? List.of() : List.copyOf(groups);
        return this;
    }
}
