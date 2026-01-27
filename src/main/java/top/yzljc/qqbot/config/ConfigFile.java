package top.yzljc.qqbot.config;

public enum ConfigFile {
    GROUP_CONFIG("groupconfig.json"),
    CONFIG("config.yml"),
    RCON_USER("adminuser.json"),
    RCON_SERVER_SECRET("server-secret.json"),
    FILTER_CONFIG("filter.yml"),
    SERVER_LIST("serverlist.json"),
    ANNOY_RECORD("annoy_user_record.json");

    private final String fileName;

    ConfigFile(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
