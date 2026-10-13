package top.yzljc.sakuraba_ema.guild.management;

import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.internal.RoleTools;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelRoles
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 */
public final class ChannelRoles {

    private ChannelRoles() {
    }

    /**
     * 创建身份组
     *
     * @param guildId QQ 频道 ID
     * @param name 身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean createGuildRoleGroup(String guildId, String name) {
        return ChannelCalls.action(() -> RoleTools.createGuildRoleGroup(guildId, name, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 修改身份组
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param name 新的身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean modifyGuildRoleGroup(String guildId, String roleId, String name) {
        return ChannelCalls.action(() -> RoleTools.modifyGuildRoleGroup(guildId, roleId, name, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 向身份组添加成员
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean addRoleMembers(String guildId, String roleId, java.util.List<String> tinyIds) {
        return ChannelCalls.action(() -> RoleTools.addRoleMembers(guildId, roleId, tinyIds, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 从身份组移除成员
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean removeRoleMembers(String guildId, String roleId, java.util.List<String> tinyIds) {
        return ChannelCalls.action(() -> RoleTools.removeRoleMembers(guildId, roleId, tinyIds, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 设置超级管理员
     *
     * @param guildId QQ 频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean addAdmin(String guildId, java.util.List<String> tinyIds) {
        return ChannelCalls.action(() -> RoleTools.addAdmin(guildId, tinyIds, ChannelCliOptions.CONFIRMED));
    }

    /**
     * 移除超级管理员
     *
     * @param guildId QQ 频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return 操作成功时为 true，调用失败时为 false
     */
    public static boolean removeAdmin(String guildId, java.util.List<String> tinyIds) {
        return ChannelCalls.action(() -> RoleTools.removeAdmin(guildId, tinyIds, ChannelCliOptions.CONFIRMED));
    }
}
