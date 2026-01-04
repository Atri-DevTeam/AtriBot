package top.yzljc.qqbot.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
                this.listenPort = (int) data.getOrDefault("listen-port", 37142);
                this.qqBotPort = (int) data.getOrDefault("qq-bot-port", 8851);
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
                this.botUid = ((Number) data.getOrDefault("bot-uid", 970717559L)).longValue();
                this.debugGroupId = ((Number) data.getOrDefault("debug-group-id", 413478250L)).longValue();
                System.out.println("Config loaded successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback defaults
            this.listenPort = 37142;
            this.qqBotPort = 8851;
            this.bilibiliCookie = "null";
            this.adminUids = new ArrayList<>();
            this.botUid = 970717559;
            this.debugGroupId = 413478250L;
            this.messageSpyGroups = new ArrayList<>();
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
}