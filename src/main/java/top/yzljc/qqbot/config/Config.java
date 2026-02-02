package top.yzljc.qqbot.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config implements Settings{

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final String CONFIG_FILE = ConfigFile.CONFIG.getFileName();
    private static Config instance;
    private int listenPort;
    private int qqBotPort;
    private String bilibiliCookie;
    private List<Long> adminUids;
    private long botUid;
    private long debugGroupId;
    private List<Long> messageSpyGroups;
    private List<Long> ignoredUsers;
    private String httpUrl;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private long manosabaGroupId;
    private boolean debugMode;
    private String[] keywordsHitokoto;
    private String[] keywordsLikeUser;
    private int githubWebhookPort;
    private String githubWebhookSecret;
    private String wakeupImgLink;
    private String commandPrefix;

    private Config() {
        load();
    }

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private void load() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            if (!Files.exists(configPath)) {
                log.info("未找到配置文件，后端程序将创建并使用默认配置config.yml");
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (in != null) {
                        Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.error("后端设计出现错误，无法找到默认的配置文件，请联系开发者解决此问题！");
                    }
                }
            }

            try (InputStream in = Files.newInputStream(configPath)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(in);

                this.listenPort = (int) data.getOrDefault("listen-port", 25566);
                this.qqBotPort = (int) data.getOrDefault("qq-bot-port", 1234);
                this.bilibiliCookie = (String) data.getOrDefault("bilibili-cookie", "null");
                this.adminUids = new ArrayList<>();
                Object adminUidsObj = data.get("admin-uids");
                if (adminUidsObj instanceof List<?>) {
                    for (Object uid : (List<?>) adminUidsObj) {
                        try {
                            long qq = ((Number) uid).longValue();
                            this.adminUids.add(qq);
                        } catch (NumberFormatException e) {
                            log.error("配置文件中存在无效管理员 UID：{}", uid);
                        }
                    }
                }
                this.messageSpyGroups = new ArrayList<>();
                Object spyGroupsObj = data.get("message-spy-groups");
                if (spyGroupsObj instanceof List<?>) {
                    for (Object gid : (List<?>) spyGroupsObj) {
                        try {
                            long groupId = ((Number) gid).longValue();
                            this.messageSpyGroups.add(groupId);
                        } catch (NumberFormatException e) {
                            log.error("配置文件中消息监听功能中出现无效群号：{}", gid);
                        }
                    }
                }
                this.botUid = ((Number) data.getOrDefault("bot-uid", 123456789L)).longValue();
                this.debugGroupId = ((Number) data.getOrDefault("debug-group-id", 123456789L)).longValue();
                this.httpUrl = (String) data.getOrDefault("napcat-data-url", "http://0.0.0.0:12345");
                this.wakeupImgLink = (String) data.getOrDefault("wakeup-image-link", "null");
                Object mysqlObj = data.get("mysql");
                if (mysqlObj instanceof Map<?, ?> rawMap) {
                    Map<String, Object> mysqlConfig = new HashMap<>();
                    rawMap.forEach((k, v) -> {
                        if (k instanceof String && v != null) {
                            mysqlConfig.put((String) k, v);
                        }
                    });
                    this.mysqlHost = (String) mysqlConfig.getOrDefault("host", "localhost");
                    this.mysqlPort = ((Number) mysqlConfig.getOrDefault("port", 3306)).intValue();
                    this.mysqlDatabase = (String) mysqlConfig.getOrDefault("database", "database");
                    this.mysqlUsername = (String) mysqlConfig.getOrDefault("username", "root");
                    this.mysqlPassword = (String) mysqlConfig.getOrDefault("password", "null");
                } else {
                    log.error("读取数据库配置时出现问题，请检查数据库配置");
                }
                this.manosabaGroupId = ((Integer) data.getOrDefault("manosaba-group-id", 123456)).longValue();
                this.debugMode = (boolean) data.getOrDefault("debug-mode", false);
                this.githubWebhookPort = (int) data.getOrDefault("github-webhook-port", 54321);
                this.githubWebhookSecret = (String) data.getOrDefault("github-webhook-secret", "null");
                this.commandPrefix = (String) data.getOrDefault("command-prefix", "/");

                this.ignoredUsers = new ArrayList<>();
                Object ignoredUserObj = data.get("recall-ignore-user");
                if (ignoredUserObj instanceof List<?>) {
                    for (Object uid : (List<?>) ignoredUserObj) {
                        try {
                            long userId = ((Number) uid).longValue();
                            this.ignoredUsers.add(userId);
                        } catch (NumberFormatException e) {
                            log.error("配置文件中屏蔽用户列表中出现无效信息：{}", uid);
                        }
                    }
                }

                Object keywordsObj = data.get("keywords-hitokoto");
                if (keywordsObj instanceof List<?>) {
                    List<String> keywordsList = new ArrayList<>();
                    for (Object kw : (List<?>) keywordsObj) {
                        if (kw instanceof String) {
                            keywordsList.add((String) kw);
                        }
                    }
                    this.keywordsHitokoto = keywordsList.toArray(new String[0]);
                } else {
                    this.keywordsHitokoto = new String[0];
                }
                Object keywordsLikeUserObj = data.get("keywords-like-user");
                if (keywordsLikeUserObj instanceof List<?>) {
                    List<String> keywordsLikeUserList = new ArrayList<>();
                    for (Object kw : (List<?>) keywordsLikeUserObj) {
                        if (kw instanceof String) {
                            keywordsLikeUserList.add((String) kw);
                        }
                    }
                    this.keywordsLikeUser = keywordsLikeUserList.toArray(new String[0]);
                } else {
                    this.keywordsLikeUser = new String[0];
                }
                log.info("配置文件加载成功");
            }
        } catch (Exception e) {
            log.warn("加载配置文件时出现错误", e);
        }
    }

    @Override
    public int getListenPort() {
        return listenPort;
    }

    @Override
    public int getQqBotPort() {
        return qqBotPort;
    }

    @Override
    public String getBilibiliCookie() {
        return bilibiliCookie;
    }

    @Override
    public List<Long> getAdminUids() {
        return new ArrayList<>(adminUids);
    }

    @Override
    public long getBotUid() {
        return botUid;
    }

    @Override
    public long getDebugGroupId() {
        return debugGroupId;
    }

    @Override
    public List<Long> getMessageSpyGroups() {
        return new ArrayList<>(messageSpyGroups);
    }

    @Override
    public String getHttpUrl() {
        return httpUrl;
    }

    @Override
    public String getMysqlHost() {
        return mysqlHost;
    }

    @Override
    public int getMysqlPort() {
        return mysqlPort;
    }

    @Override
    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    @Override
    public String getMysqlUsername() {
        return mysqlUsername;
    }

    @Override
    public String getMysqlPassword() {
        return mysqlPassword;
    }

    @Override
    public long getManosabaGroupId() {
        return manosabaGroupId;
    }

    @Override
    public List<Long> getIgnoredUsers() {
        return new ArrayList<>(ignoredUsers);
    }

    @Override
    public boolean isDebugMode() {
        return debugMode;
    }

    @Override
    public String[] getKeywordsHitokoto() {
        return keywordsHitokoto;
    }

    @Override
    public String[] getKeywordsLikeUser() {
        return keywordsLikeUser;
    }

    @Override
    public String getGithubWebhookSecret() {
        return githubWebhookSecret;
    }

    @Override
    public int getGithubWebhookPort() {
        return githubWebhookPort;
    }

    @Override
    public String getWakeupImgLink() {
        return wakeupImgLink;
    }

    @Override
    public String getCommandPrefix() {
        return commandPrefix;
    }
}
