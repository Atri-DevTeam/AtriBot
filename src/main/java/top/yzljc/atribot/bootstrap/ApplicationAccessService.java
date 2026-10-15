package top.yzljc.atribot.bootstrap;

import top.yzljc.atricore.core.AccessService;
import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.OfficialGroups;

/**
 * @Author YZ_Ljc_
 * @ClassName ApplicationAccessService
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.bootstrap
 */
public final class ApplicationAccessService extends AccessService {
    @Override
    public AtriAccount findByQqUserOpenId(String openId) {
        return UnifiedAuthentication.findByQqUserOpenId(openId);
    }

    @Override
    public AtriAccount findByQqUserUin(String uin) {
        return UnifiedAuthentication.findByQqUserUin(uin);
    }

    @Override
    public boolean isAdmin(String userId) {
        return OfficialUsers.isAdmin(userId);
    }

    @Override
    public boolean hasPermission(String userId, String permission) {
        return OfficialUsers.hasPermission(userId, permission);
    }

    @Override
    public boolean isBlocked(String userId) {
        return OfficialUsers.isBlocked(userId);
    }

    @Override
    public boolean isIgnored(String userId) {
        return OfficialUsers.isIgnored(userId);
    }

    @Override
    public boolean isGroupBlacklisted(String groupId) {
        return OfficialGroups.isGroupBlacklisted(groupId);
    }

    @Override
    public UnifiedRole getRole(String userId) {
        return OfficialUsers.getRole(userId);
    }

    @Override
    public boolean allowProactiveMsg(String groupId) {
        return OfficialGroups.allowProactiveMsg(groupId);
    }

    @Override
    public void setAllowProactiveMsg(String groupId, boolean allowed) {
        OfficialGroups.setAllowProactiveMsg(groupId, allowed);
    }

}
