package top.yzljc.qqbot.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config implements Settings{
    private static final String CONFIG_FILE = "config.yml";
    private static Config instance;
    private int listenPort;
    private int qqBotPort;
    private String bilibiliCookie;
    private List<Long> adminUids;
    private long botUid;
    private long debugGroupId;
    private List<Long> messageSpyGroups;
    private String httpUrl;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private long manosabaGroupId;

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
            // Check if config exists in run directory, if not, copy from resources
            if (!Files.exists(Paths.get(CONFIG_FILE))) {
                System.out.println("Config file not found, creating default config.yml...");
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (in != null) {
                        Files.copy(in, Paths.get(CONFIG_FILE), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        System.err.println("Default config.yml not found in resources!");
                    }
                }
            }

            // Load config
            try (InputStream in = Files.newInputStream(Paths.get(CONFIG_FILE))) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(in);

                // Parse values with defaults if missing
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
                            System.err.println("Invalid admin UID in config: " + uid);
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
                            System.err.println("Invalid message spy group ID in config: " + gid);
                        }
                    }
                }
                this.botUid = ((Number) data.getOrDefault("bot-uid", 123456789L)).longValue();
                this.debugGroupId = ((Number) data.getOrDefault("debug-group-id", 123456789L)).longValue();
                this.httpUrl = (String) data.getOrDefault("napcat-data-url", "http://0.0.0.0:12345");
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
                    this.mysqlHost = "localhost";
                    this.mysqlPort = 3306;
                    this.mysqlDatabase = "database";
                    this.mysqlUsername = "root";
                    this.mysqlPassword = "null";
                }
                this.manosabaGroupId = ((Integer) data.getOrDefault("manosaba-group-id", 123456)).longValue();
                System.out.println("Config loaded successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
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
}