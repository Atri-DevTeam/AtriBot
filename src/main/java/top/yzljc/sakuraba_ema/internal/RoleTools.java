package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName RoleTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道身份组与管理员管理
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
public final class RoleTools {

    private RoleTools() {
    }

    /**
     * 创建身份组，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param name 身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createGuildRoleGroup(String guildId, String name, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("name", name);
        return ChannelCalls.client().execute("manage", "create-guild-role-group", parameters, options);
    }

    /**
     * 修改身份组，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param name 新的身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyGuildRoleGroup(String guildId, String roleId, String name, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (roleId == null || roleId.isBlank()) throw new IllegalArgumentException("role-id 不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("role_id", roleId);
        parameters.put("name", name);
        return ChannelCalls.client().execute("manage", "modify-guild-role-group", parameters, options);
    }

    /**
     * 向身份组添加成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult addRoleMembers(String guildId, String roleId, java.util.List<String> tinyIds, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (roleId == null || roleId.isBlank()) throw new IllegalArgumentException("role-id 不能为空");
        if (tinyIds == null || tinyIds.isEmpty() || tinyIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("tiny-ids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("role_id", roleId);
        var tinyIdsArray = parameters.putArray("tiny_ids");
        tinyIds.forEach(tinyIdsArray::add);
        return ChannelCalls.client().execute("manage", "add-role-members", parameters, options);
    }

    /**
     * 从身份组移除成员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeRoleMembers(String guildId, String roleId, java.util.List<String> tinyIds, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (roleId == null || roleId.isBlank()) throw new IllegalArgumentException("role-id 不能为空");
        if (tinyIds == null || tinyIds.isEmpty() || tinyIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("tiny-ids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("role_id", roleId);
        var tinyIdsArray = parameters.putArray("tiny_ids");
        tinyIds.forEach(tinyIdsArray::add);
        return ChannelCalls.client().execute("manage", "remove-role-members", parameters, options);
    }

    /**
     * 设置超级管理员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult addAdmin(String guildId, java.util.List<String> tinyIds, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (tinyIds == null || tinyIds.isEmpty() || tinyIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("tiny-ids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        var tinyIdsArray = parameters.putArray("tiny_ids");
        tinyIds.forEach(tinyIdsArray::add);
        return ChannelCalls.client().execute("manage", "add-admin", parameters, options);
    }

    /**
     * 移除超级管理员，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeAdmin(String guildId, java.util.List<String> tinyIds, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (tinyIds == null || tinyIds.isEmpty() || tinyIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("tiny-ids 不能为空或包含空项");
        }
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        var tinyIdsArray = parameters.putArray("tiny_ids");
        tinyIds.forEach(tinyIdsArray::add);
        return ChannelCalls.client().execute("manage", "remove-admin", parameters, options);
    }
}
