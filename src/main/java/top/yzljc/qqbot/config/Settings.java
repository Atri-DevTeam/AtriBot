package top.yzljc.qqbot.config;

import java.util.List;

public interface Settings {

    int getListenPort();

    int getQqBotPort();

    String getBilibiliCookie();

    List<Long> getAdminUids();

    /**
     * 不再通过配置文件手动填写
     * 改为通过请求自动获取并储存
     */
    @Deprecated(since = "2.6.0")
    long getBotUid();

    long getDebugGroupId();

    List<Long> getMessageSpyGroups();

    String getHttpUrl();

    String getMysqlHost();

    int getMysqlPort();

    String getMysqlDatabase();

    String getMysqlUsername();

    String getMysqlPassword();

    long getManosabaGroupId();

    boolean isDebugMode();

    String[] getKeywordsHitokoto();

    String[] getKeywordsLikeUser();

    String getGithubWebhookSecret();

    int getGithubWebhookPort();

    List<Long> getIgnoredUsers();

    String getWakeupImgLink();

    String getCommandPrefix();

    String getWebsocketUrl();

    String getDebugCommandSuffix();

    String getTtfFileName();
}
