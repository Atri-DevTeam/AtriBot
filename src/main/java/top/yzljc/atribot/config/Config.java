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

    // ########## 全局设置区域 ##########
    @Getter
    private String commandPrefix;
    @Getter
    private String debugCommandSuffix;
    @Getter
    private String ttfFileName;
    @Getter
    private boolean debugMode;
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
    private String aiApiKey;
    private String aiBaseUrl;
    private String aiModel;
    private int aiTimeout;
    @Getter
    private int listenPort;

    // ########## Napcat设置区域 ##########
    @Getter
    private boolean napcatEnabled;
    @Getter
    private String napcatServerUrl;
    @Getter
    private long napcatDebugGroupUin;
    @Getter
    private long napcatBotUin;
    @Getter
    private List<Long> napcatAdminUins;
    @Getter
    private List<Long> napcatMessageSpyGroups;
    @Getter
    private List<Long> napcatRecallIgnoredUsers;

    // ########## 功能设置区域 ##########
    @Getter
    private String atribotKeySecret;
    @Getter
    private String hypixelRewardWebSocketUrl;
    @Getter
    private String saSignSecretKey;
    @Getter
    private int githubWebhookPort;
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

    // ########## 官方机器人配置参数 ##########
    @Getter
    private boolean officialBotEnabled;
    @Getter
    private String qqAppId;
    @Getter
    private String qqClientSecret;
    @Getter
    private String qqApiBaseUrl;
    @Getter
    private String officialWebuiToken;
    @Getter
    private String officialOpenId;
    @Getter
    private String officialUsername;
    @Getter
    private String debugGroupOpenId;
    @Getter
    private boolean newBot;

    // ########## 特殊群专用内容设置区域 ##########
    @Getter
    private long manosabaGroupId;
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
            this.commandPrefix = yaml.getString("command-prefix", "/");
            this.debugCommandSuffix = yaml.getString("debug-command-suffix", "--debug");
            this.ttfFileName = yaml.getString("ttf-file-name", "default.ttf");
            this.debugMode = yaml.getBoolean("debug-mode", false);
            this.mysqlHost = yaml.getString("mysql.host", "localhost");
            this.mysqlPort = yaml.getInt("mysql.port", 3306);
            this.mysqlDatabase = yaml.getString("mysql.database", "database");
            this.mysqlUsername = yaml.getString("mysql.username", "root");
            this.mysqlPassword = yaml.getString("mysql.password", "null");
            this.aiApiKey = yaml.getString("ai.api-key", "");
            this.aiBaseUrl = yaml.getString("ai.base-url", "");
            this.aiModel = yaml.getString("ai.model", "qwen3.5-flash");
            this.aiTimeout = yaml.getInt("ai.timeout", 30000);
            this.listenPort = yaml.getInt("listen-port", 1234);

            // ########## Napcat设置区域 ##########
            this.napcatEnabled = yaml.getBoolean("napcat.enabled", false);
            this.napcatServerUrl = yaml.getString("napcat.server-url", "http://0.0.0.0:12345");
            this.napcatDebugGroupUin = getLongOrDefault("napcat.debug-group-uin", 123456789L);
            this.napcatBotUin = getLongOrDefault("napcat.bot-uin", 123456789L);
            this.napcatAdminUins = parseLongList(yaml.getStringList("napcat.admin-uins"));
            this.napcatMessageSpyGroups = parseLongList(yaml.getStringList("napcat.message-spy-groups"));
            this.napcatRecallIgnoredUsers = parseLongList(yaml.getStringList("napcat.recall-ignore-user"));

            // ########## 功能设置区域 ##########
            this.atribotKeySecret = yaml.getString("atribot-key-secret", "null");
            this.hypixelRewardWebSocketUrl = yaml.getString("function.hypixel-reward-ws", "ws://localhost:1111");
            this.saSignSecretKey = yaml.getString("function.sa-sign-key", "null");
            this.githubWebhookPort = yaml.getInt("function.github-webhook.port", 54321);
            this.githubWebhookSecret = yaml.getString("function.github-webhook.secret", "null");
            this.bilibiliCookie = yaml.getString("function.bilibili-cookie", "null");
            this.verifyEnabled = yaml.getBoolean("verify.enabled", false);
            this.verifyPort = yaml.getInt("verify.port", 8080);
            this.verifyHost = yaml.getString("verify.host", "127.0.0.1");
            this.verifyKey = yaml.getString("verify.key", "public-key");
            this.wakeupImgLink = yaml.getString("function.wakeup-image-link", "null");
            this.keywordsHitokoto = yaml.getStringList("function.keywords-hitokoto").toArray(new String[0]);
            this.keywordsLikeUser = yaml.getStringList("function.keywords-like-user").toArray(new String[0]);

            // ########## 官方机器人配置参数 ##########
            this.officialBotEnabled = yaml.getBoolean("qq.enabled", false);
            this.qqAppId = yaml.getString("qq.app-id", "");
            this.qqClientSecret = yaml.getString("qq.client-secret", "");
            this.qqApiBaseUrl = yaml.getString("qq.api-base-url", "https://sandbox.api.sgroup.qq.com");
            this.officialOpenId = yaml.getString("qq.official-openId", "null");
            this.officialUsername = yaml.getString("qq.official-username", "null");
            this.officialWebuiToken = yaml.getString("qq.official-webui-token", "null");
            this.debugGroupOpenId = yaml.getString("qq.debug-group-openId", "null");
            this.newBot = yaml.getBoolean("qq.is-new-bot", false);

            // ########## 特殊群专用内容设置区域 ##########
            this.manosabaGroupId = getLongOrDefault("manosaba-group-id", 123456L);

            // 群验证配置
            this.groupJoinVerifyGroupId = getLongOrDefault("group-join-verify-group-id", 123456789L);
            this.groupJoinVerifyMessage = yaml.getString("group-join-verify-message", "欢迎加入，请在30秒内发送验证消息，否则将被移出群聊");
            this.groupJoinVerifyTimeoutSeconds = yaml.getInt("group-join-verify-timeout-seconds", 60);
            this.groupJoinVerifyAnswer = yaml.getString("group-join-verify-answer", "验证");
            this.activeMessageGroups = yaml.getStringList("official-active-message-groups");

            this.yunlandHost = yaml.getString("yunland.host", "null");
            this.yunlandPort = yaml.getInt("yunland.port", 12345);
            this.yunlandConnectKey = yaml.getString("yunland.connect-key", "null");

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
