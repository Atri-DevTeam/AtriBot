package top.yzljc.atricore.core;

import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.auth.official.UnifiedRole;

/**
 * 运行服务提供的账号与访问控制扩展点，默认不授予权限，也不保存状态
 *
 * @Author YZ_Ljc_
 * @ClassName AccessService
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
public class AccessService {
    /**
     * 根据 QQ 用户 OpenID 查询运行服务中的账号
     *
     * @param openId QQ 用户 OpenID，频道事件未提供统一标识时可能为 null
     * @return 关联账号，未找到时返回 null
     */
    public AtriAccount findByQqUserOpenId(String openId) {
        return null;
    }

    /**
     * 根据 QQ 号码查询运行服务中的账号
     *
     * @param uin QQ 号码
     * @return 关联账号，未找到时返回 null
     */
    public AtriAccount findByQqUserUin(String uin) {
        return null;
    }

    /**
     * 判断用户是否具有机器人管理权限
     *
     * @param userId 调用平台使用的用户标识
     * @return 具有机器人管理权限时返回 true，不应仅依据平台群内角色判断
     */
    public boolean isAdmin(String userId) {
        return false;
    }

    /**
     * 查询运行服务授予用户的权限节点
     *
     * @param userId     调用平台使用的用户标识
     * @param permission 权限节点
     * @return 拥有指定权限时返回 true
     */
    public boolean hasPermission(String userId, String permission) {
        return false;
    }

    /**
     * 查询用户封禁状态
     *
     * @param userId 调用平台使用的用户标识
     * @return 用户已被封禁时返回 true
     */
    public boolean isBlocked(String userId) {
        return false;
    }

    /**
     * 查询用户忽略状态
     *
     * @param userId 调用平台使用的用户标识
     * @return 运行服务要求忽略该用户时返回 true
     */
    public boolean isIgnored(String userId) {
        return false;
    }

    /**
     * 查询群黑名单状态
     *
     * @param groupId 群标识
     * @return 群已被列入黑名单时返回 true
     */
    public boolean isGroupBlacklisted(String groupId) {
        return false;
    }

    /**
     * 查询用户在机器人系统中的角色
     *
     * @param userId 调用平台使用的用户标识
     * @return 账号角色，默认返回 USER
     */
    public UnifiedRole getRole(String userId) {
        return UnifiedRole.USER;
    }

    /**
     * 查询运行服务记录的 QQ 群主动消息状态
     *
     * @param groupId QQ 群 OpenID
     * @return 记录为允许主动消息时返回 true，不代表平台保证发送成功
     */
    public boolean allowProactiveMsg(String groupId) {
        return false;
    }

    /**
     * 更新 QQ 群主动消息状态，默认实现不保存数据
     *
     * @param groupId QQ 群 OpenID
     * @param allowed 是否允许主动消息
     */
    public void setAllowProactiveMsg(String groupId, boolean allowed) {
    }
}
