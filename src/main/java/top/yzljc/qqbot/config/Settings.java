package top.yzljc.qqbot.config;

import java.util.List;

public interface Settings {

    int getListenPort();

    int getQqBotPort();

    String getBilibiliCookie();

    List<Long> getAdminUids();

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
}
