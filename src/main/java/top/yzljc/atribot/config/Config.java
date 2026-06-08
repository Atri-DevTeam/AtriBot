package top.yzljc.atribot.config;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final String CONFIG_FILE = ConfigFile.CONFIG.getFileName();
    private static Config instance;

    private YamlConfiguration yaml;

    @Getter
    private int qqBotPort;
    @Getter
    private String bilibiliCookie;
    @Getter
    private List<Long> adminUids;
    @Getter
    private long botUid;
    @Getter
    private long debugGroupId;
    @Getter
    private String debugGroupOpenId;
    @Getter
    private List<Long> messageSpyGroups;
    @Getter
    private List<Long> ignoredUsers;
    @Getter
    private String httpUrl;
    @Getter
    private String mysqlHost;
    @Getter
    private int mysqlPort;
    @Getter
    private String mysqlDatabase;
    @Getter
    private String mysqlUsername;
    @Getter
    private String mysqlPassword;
    @Getter
    private long manosabaGroupId;
    @Getter
    private boolean debugMode;
    @Getter
    private String[] keywordsHitokoto;
    @Getter
    private String[] keywordsLikeUser;
    @Getter
    private int githubWebhookPort;
    @Getter
    private String githubWebhookSecret;
    @Getter
    private String wakeupImgLink;
    @Getter
    private String commandPrefix;
    @Getter
    private String websocketUrl;
    @Getter
    private String debugCommandSuffix;
    @Getter
    private String ttfFileName;
    @Getter
    private int varietyPort;
    @Getter
    private String varietyHost;
    @Getter
    private String varietyKey;

    @Getter
    private String saSignSecretKey;

    private String aiApiKey;
    private String aiBaseUrl;
    private String aiModel;
    private int aiTimeout;

    @Getter
    private String qqAppId;
    @Getter
    private String qqClientSecret;
    @Getter
    private String qqApiBaseUrl;

    @Getter
    private String groupJoinVerifyMessage;
    @Getter
    private String groupJoinVerifyAnswer;
    @Getter
    private long groupJoinVerifyGroupId;
    @Getter
    private int groupJoinVerifyTimeoutSeconds;
    @Getter
    private List<String> activeMessageGroups;
    @Getter
    private String yunlandHost;
    @Getter
    private int yunlandPort;
    @Getter
    private String yunlandConnectKey;
    @Getter
    private String atribotKeySecret;
    @Getter
    private String officialWebuiToken;
    @Getter
    private String officialOpenId;
    @Getter
    private String officialUsername;

    private Config() {
        load();
    }

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public void reload() {
        log.info("开始重新加载配置文件...");
        load();
    }

    private void load() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            if (!Files.exists(configPath)) {
                log.info("未找到配置文件，AtriBot将创建并使用默认配置: {}", CONFIG_FILE);
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (in != null) {
                        Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.error("AtriBot设计出现错误，无法找到默认的配置文件，请联系开发者解决此问题！");
                    }
                }
            }

            this.yaml = new YamlConfiguration(configPath.toFile());
            yaml.load();

            this.qqBotPort = yaml.getInt("qq-bot-port", 1234);
            this.bilibiliCookie = yaml.getString("bilibili-cookie", "null");
            this.botUid = getLongOrDefault("bot-uid", 123456789L);
            this.debugGroupId = getLongOrDefault("debug-group-id", 123456789L);
            this.debugGroupOpenId = yaml.getString("debug-group-openId", "null");
            this.httpUrl = yaml.getString("napcat-data-url", "http://0.0.0.0:12345");
            this.wakeupImgLink = yaml.getString("wakeup-image-link", "null");
            this.websocketUrl = yaml.getString("websocket-url", "ws://localhost:1111");
            this.debugCommandSuffix = yaml.getString("debug-command-suffix", "--debug");

            // 读取嵌套的 MySQL 配置
            this.mysqlHost = yaml.getString("mysql.host", "localhost");
            this.mysqlPort = yaml.getInt("mysql.port", 3306);
            this.mysqlDatabase = yaml.getString("mysql.database", "database");
            this.mysqlUsername = yaml.getString("mysql.username", "root");
            this.mysqlPassword = yaml.getString("mysql.password", "null");

            this.manosabaGroupId = getLongOrDefault("manosaba-group-id", 123456L);
            this.debugMode = yaml.getBoolean("debug-mode", false);
            this.githubWebhookPort = yaml.getInt("github-webhook-port", 54321);
            this.githubWebhookSecret = yaml.getString("github-webhook-secret", "null");
            this.commandPrefix = yaml.getString("command-prefix", "/");
            this.ttfFileName = yaml.getString("ttf-file-name", "default.ttf");
            this.varietyPort = yaml.getInt("variety-port", 8080);
            this.varietyHost = yaml.getString("variety-host", "127.0.0.1");
            this.varietyKey = yaml.getString("variety-key", "public-key");
            this.saSignSecretKey = yaml.getString("sa-sign-key", "null");

            // 读取嵌套的 AI 配置
            this.aiApiKey = yaml.getString("ai.api-key", "");
            this.aiBaseUrl = yaml.getString("ai.base-url", "");
            this.aiModel = yaml.getString("ai.model", "qwen3.5-flash");
            this.aiTimeout = yaml.getInt("ai.timeout", 30000);

            // 读取嵌套的 QQ 配置
            this.qqAppId = yaml.getString("qq.app-id", "");
            this.qqClientSecret = yaml.getString("qq.client-secret", "");
            this.qqApiBaseUrl = yaml.getString("qq.api-base-url", "https://sandbox.api.sgroup.qq.com");

            // 群验证配置
            this.groupJoinVerifyGroupId = getLongOrDefault("group-join-verify-group-id", 123456789L);
            this.groupJoinVerifyMessage = yaml.getString("group-join-verify-message", "欢迎加入，请在30秒内发送验证消息，否则将被移出群聊");
            this.groupJoinVerifyTimeoutSeconds = yaml.getInt("group-join-verify-timeout-seconds", 60);
            this.groupJoinVerifyAnswer = yaml.getString("group-join-verify-answer", "验证");

            // 自动将 yaml 的列表解析为 Long 列表
            this.adminUids = parseLongList(yaml.getStringList("admin-uids"));
            this.messageSpyGroups = parseLongList(yaml.getStringList("message-spy-groups"));
            this.ignoredUsers = parseLongList(yaml.getStringList("recall-ignore-user"));

            // 解析 String 数组
            this.keywordsHitokoto = yaml.getStringList("keywords-hitokoto").toArray(new String[0]);
            this.keywordsLikeUser = yaml.getStringList("keywords-like-user").toArray(new String[0]);
            this.activeMessageGroups = yaml.getStringList("official-active-message-groups");

            this.yunlandHost = yaml.getString("yunland.host", "null");
            this.yunlandPort = yaml.getInt("yunland.port", 12345);
            this.yunlandConnectKey = yaml.getString("yunland.connect-key", "null");

            this.atribotKeySecret = yaml.getString("atribot-key-secret", "null");
            this.officialWebuiToken = yaml.getString("official-webui-token", "null");
            this.officialOpenId = yaml.getString("official-openId", "null");
            this.officialUsername = yaml.getString("official-username", "null");

            log.info("配置文件加载成功");

        } catch (Exception e) {
            log.warn("加载配置文件时出现错误", e);
        }
    }

    // 将字符串列表安全转换为 Long 列表
    private List<Long> parseLongList(List<String> stringList) {
        List<Long> result = new ArrayList<>();
        for (String s : stringList) {
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                log.error("配置文件中存在无效的数字 ID：{}", s);
            }
        }
        return result;
    }

    // 获取 Long 类型并支持默认值
    private long getLongOrDefault(String path, long def) {
        Object value = yaml.get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return def;
    }

    public AiProperties getAiBotProperties() {
        AiProperties props = new AiProperties();
        props.setApiKey(aiApiKey);
        props.setBaseUrl(aiBaseUrl);
        props.setModel(aiModel);
        props.setTimeout(aiTimeout);
        return props;
    }
}
