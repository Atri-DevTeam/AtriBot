package top.yzljc.atribot.configuration;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.service.ai.AiProperties;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.utils.YamlConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final String CONFIG_FILE = Properties.CONFIG;
    private static final Set<String> STANDARD_AI_KEYS = Set.of("api-key", "base-url", "model", "timeout");
    private static Config instance;

    private YamlConfiguration yaml;

    // ########## 全局设置区域 ##########
    @Getter
    private String debugCommandSuffix;
    @Getter
    private String ttfFileName;
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
    private Map<AiProvider, AiProperties> aiPropertiesMap;
    @Getter
    private String env;
    @Getter
    private String apiUrl;
    @Getter
    private boolean miniappEnabled;
    @Getter
    private boolean dashboardEnabled;
    @Getter
    private String dashboardBaseUrl;
    @Getter
    private String dashboardCallbackUrl;
    @Getter
    private String qqConnectAppId;
    @Getter
    private String qqConnectAppKey;
    @Getter
    private String ugcApiUrl;
    @Getter
    private String ossDumpBaseUrl;
    @Getter
    private String soundResourceBaseUrl;
    @Getter
    private String soundIndexPath;
    @Getter
    private int soundAnswerSeconds;
    @Getter
    private String minecraftModerationReviewKey;
    @Getter
    private String censorApiUrl;
    @Getter
    private String censorApiKey;

    // ########## Napcat设置区域 ##########
    @Getter
    private List<String> napcatRecallIgnoredUsers;

    // ########## 功能设置区域 ##########
    private String atribotKeySecret;
    @Getter
    private String hypixelRewardWebSocketUrl;
    @Getter
    private String saSignSecretKey;
    @Getter
    private String githubWebhookPath;
    @Getter
    private String githubWebhookSecret;
    @Getter
    private String bilibiliCookie;
    @Getter
    private String wakeupImgLink;
    @Getter
    private String[] keywordsHitokoto;
    @Getter
    private String[] keywordsLikeUser;
    @Getter
    private boolean verifyEnabled;
    @Getter
    private int verifyPort;
    @Getter
    private String verifyHost;
    @Getter
    private String verifyKey;
    @Getter
    private String US_API;
    @Getter
    private String backendApi;
    private java.util.Properties emailProperties;
    @Getter
    private String verifyStrategyId;

    // ########## 官方机器人配置参数 ##########
    @Getter
    private String officialOpenId;
    @Getter
    @Deprecated(since = "3.1.7") // 直接 /users/@me 拿，硬编码狗都不用
    private String officialUsername;
    @Getter
    private String superAdminId;

    // ########## 腾讯频道 CLI（第二账号）配置参数 ##########
    @Getter
    private boolean tencentChannelEnabled;
    @Getter
    private String tencentChannelCliPath;
    @Getter
    private String tencentChannelLoginToken;
    @Getter
    private int tencentChannelTimeoutSeconds;

    // Email configuration
    @Getter
    private boolean emailEnabled;
    @Getter
    private String emailUsername;
    @Getter
    private String emailPassword;
    private String protocol;
    private String host;
    private int port;
    private boolean sslEnabled;
    private int connectionTimeout;
    private int readTimeout;

    // ########## 特殊群专用内容设置区域 ##########
    @Getter
    private String manosabaGroupId;
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
    private boolean imageSourceEnabled;
    @Getter
    private int imageSourcePendingLimit;

    // ########## 抽卡(Loots)设置区域 ##########
    @Getter
    private String lootsAdminToken;

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

            // ########## 全局设置区域 ##########
            this.debugCommandSuffix = yaml.getString("debug-command-suffix", "--debug");
            this.ttfFileName = yaml.getString("ttf-file-name", "default.ttf");
            this.mysqlHost = yaml.getString("mysql.host", "localhost");
            this.mysqlPort = yaml.getInt("mysql.port", 3306);
            this.mysqlDatabase = yaml.getString("mysql.database", "database");
            this.mysqlUsername = yaml.getString("mysql.username", "root");
            this.mysqlPassword = yaml.getString("mysql.password", "null");
            this.aiPropertiesMap = loadAiConfigs();
            this.env = yaml.getString("env", "production");
            this.apiUrl = yaml.getString("api-url", "http://localhost:1234");
            this.miniappEnabled = yaml.getBoolean("miniapp.enabled", true);
            this.dashboardEnabled = yaml.getBoolean("dashboard.enabled", false);
            this.dashboardBaseUrl = yaml.getString("dashboard.base-url", "");
            this.dashboardCallbackUrl = yaml.getString("dashboard.callback-url", "");
            this.qqConnectAppId = yaml.getString("dashboard.qq-connect.app-id", "");
            this.qqConnectAppKey = yaml.getString("dashboard.qq-connect.app-key", "");
            this.ugcApiUrl = yaml.getString("ugc-api-url", "http://localhost:1234");
            this.ossDumpBaseUrl = yaml.getString("delivery.oss-dump-base-url", "null");
            this.soundResourceBaseUrl = yaml.getString("sound.resource-base-url", "");
            this.soundIndexPath = yaml.getString("sound.index-path", "minecraft/26.3-rc-3/index.json");
            this.soundAnswerSeconds = yaml.getInt("sound.answer-seconds", 120);
            this.minecraftModerationReviewKey = yaml.getString("minecraft-moderation.review-key", "");
            this.censorApiUrl = yaml.getString("censor.api-url", "");
            this.censorApiKey = yaml.getString("censor.api-key", "");

            // ########## Napcat设置区域 ##########
            this.napcatRecallIgnoredUsers = yaml.getStringList("napcat.recall-ignore-user");

            // ########## 功能设置区域 ##########
            this.atribotKeySecret = yaml.getString("atribot-key-secret", "null");
            this.hypixelRewardWebSocketUrl = yaml.getString("function.hypixel-reward-ws", "ws://localhost:1111");
            this.saSignSecretKey = yaml.getString("function.sa-sign-key", "null");
            this.githubWebhookPath = yaml.getString("function.github-webhook.path", "/github-webhook");
            this.githubWebhookSecret = yaml.getString("function.github-webhook.secret", "null");
            this.bilibiliCookie = yaml.getString("function.bilibili-cookie", "null");
            this.verifyEnabled = yaml.getBoolean("verify.enabled", false);
            this.verifyPort = yaml.getInt("verify.port", 8080);
            this.verifyHost = yaml.getString("verify.host", "127.0.0.1");
            this.verifyKey = yaml.getString("verify.key", "public-key");
            this.wakeupImgLink = yaml.getString("function.wakeup-image-link", "null");
            this.keywordsHitokoto = yaml.getStringList("function.keywords-hitokoto").toArray(new String[0]);
            this.keywordsLikeUser = yaml.getStringList("function.keywords-like-user").toArray(new String[0]);
            this.US_API = yaml.getString("function.us-api", "null");
            this.verifyStrategyId = yaml.getString("ua.verify.strategy-id", "default");
            this.backendApi = yaml.getString("backend.api", "null");

            // -------- Email 配置区域 ---------
            this.emailEnabled = yaml.getBoolean("email.enabled", false);
            this.emailUsername = yaml.getString("email.username", "");
            this.emailPassword = yaml.getString("email.password", "");
            this.protocol = yaml.getString("email.protocol", "imap");
            this.host = yaml.getString("email.host", "imap.qq.com");
            this.port = yaml.getInt("email.port", 993);
            this.sslEnabled = yaml.getBoolean("email.ssl-enabled", true);
            this.connectionTimeout = yaml.getInt("email.connection-timeout", 30000);
            this.readTimeout = yaml.getInt("email.read-timeout", 300000);
            this.emailProperties = getEmailProperties();

            // ########## 官方机器人配置参数 ##########
            this.officialOpenId = yaml.getString("qq.official-openId", "null");
            this.officialUsername = yaml.getString("qq.official-username", "null");
            this.superAdminId = yaml.getString("qq.super_admin_id", "null");

            // ########## 腾讯频道 CLI（第二账号）配置参数 ##########
            this.tencentChannelEnabled = yaml.getBoolean("tencent-channel.enabled", false);
            this.tencentChannelCliPath = yaml.getString("tencent-channel.cli-path", "tencent-channel-cli");
            String channelTokenFromEnv = System.getenv("TENCENT_CHANNEL_LOGIN_TOKEN");
            this.tencentChannelLoginToken = channelTokenFromEnv != null && !channelTokenFromEnv.isBlank()
                    ? channelTokenFromEnv
                    : yaml.getString("tencent-channel.login-token", "");
            this.tencentChannelTimeoutSeconds = Math.max(1,
                    yaml.getInt("tencent-channel.timeout-seconds", 90));

            // ########## 特殊群专用内容设置区域 ##########
            this.manosabaGroupId = yaml.getString("manosaba-group-id", "null");

            // 群验证配置
            this.groupJoinVerifyGroupId = getLongOrDefault("group-join-verify-group-id", 123456789L);
            this.groupJoinVerifyMessage = yaml.getString("group-join-verify-message", "欢迎加入，请在30秒内发送验证消息，否则将被移出群聊");
            this.groupJoinVerifyTimeoutSeconds = yaml.getInt("group-join-verify-timeout-seconds", 60);
            this.groupJoinVerifyAnswer = yaml.getString("group-join-verify-answer", "验证");
            this.activeMessageGroups = yaml.getStringList("official-active-message-groups");

            this.yunlandHost = yaml.getString("yunland.host", "null");
            this.yunlandPort = yaml.getInt("yunland.port", 12345);
            this.yunlandConnectKey = yaml.getString("yunland.connect-key", "null");

            // ########## 图源投稿设置区域 ##########
            this.imageSourceEnabled = yaml.getBoolean("image-source.enabled", false);
            this.imageSourcePendingLimit = yaml.getInt("image-source.pending-limit", 3);

            // ########## 抽卡(Loots)设置区域 ##########
            this.lootsAdminToken = yaml.getString("loots.admin-token", "null");

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

    private Map<AiProvider, AiProperties> loadAiConfigs() {
        Map<AiProvider, AiProperties> map = new EnumMap<>(AiProvider.class);

        for (AiProvider provider : AiProvider.values()) {
            String key = "ai." + provider.getConfigKey();
            AiProperties props = loadAiProperties(key);
            map.put(provider, props);
        }

        // 确保 DEFAULT 至少有一个空配置，防止 NPE
        AiProperties defaultProps = map.get(AiProvider.DEFAULT);
        if (defaultProps == null || defaultProps.getBaseUrl().isEmpty()) {
            log.warn("AI 默认配置 (ai.default) 未设置或 base-url 为空，AI 功能可能不可用");
        }

        log.info("已加载 {} 个 AI 配置: {}", map.size(), map.keySet());
        return map;
    }

    private AiProperties loadAiProperties(String prefix) {
        AiProperties props = new AiProperties();
        props.setApiKey(yaml.getString(prefix + ".api-key", ""));
        props.setBaseUrl(yaml.getString(prefix + ".base-url", ""));
        props.setModel(yaml.getString(prefix + ".model", "deepseek-v4-pro"));
        props.setTimeout(yaml.getInt(prefix + ".timeout", 30000000));

        // 读取 YAML 中除标准字段外的所有额外字段，放入 extraBody
        YamlConfiguration.ConfigSection section = yaml.getSection(prefix);
        if (section != null) {
            for (String key : section.getKeys()) {
                if (!STANDARD_AI_KEYS.contains(key)) {
                    props.getExtraBody().put(key, section.get(key));
                }
            }
        }

        return props;
    }

    public AiProperties getAiBotProperties() {
        return getAiBotProperties(AiProvider.DEFAULT);
    }

    public AiProperties getAiBotProperties(AiProvider provider) {
        AiProperties props = aiPropertiesMap.get(provider);
        if (props == null) {
            props = aiPropertiesMap.get(AiProvider.DEFAULT);
        }
        return props;
    }

    public java.util.Properties getEmailProperties() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.store.protocol", protocol);
        props.setProperty("mail.imap.host", host);
        props.setProperty("mail.imap.port", String.valueOf(port));
        props.setProperty("mail.imap.ssl.enable", String.valueOf(sslEnabled));
        props.setProperty("mail.imap.connectiontimeout", String.valueOf(connectionTimeout));
        props.setProperty("mail.imap.timeout", String.valueOf(readTimeout));
        return props;
    }

    public String getAtribotKeySecret() {
        return this.atribotKeySecret;
    }
}
