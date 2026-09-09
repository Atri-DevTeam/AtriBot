package top.yzljc.sakuraba_ema.guild.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelRoles
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.guild.management
 * @Description 频道身份组与管理员管理
 *
 * 静态同步业务入口，内部复用配置中的频道第二账号 CLI 客户端。
 * 返回原始执行结果；业务失败查看 success/getError，执行异常抛出 ChannelCliException。
 */
public final class ChannelRoles {

    private ChannelRoles() {
    }

    /**
     * 创建身份组
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.create-guild-role-group}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #createGuildRoleGroup(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult createGuildRoleGroup(JsonNode parameters) {
        return createGuildRoleGroup(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 创建身份组，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage create-guild-role-group 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult createGuildRoleGroup(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "create-guild-role-group", parameters, options);
    }

    /**
     * 修改身份组
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.modify-guild-role-group}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #modifyGuildRoleGroup(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult modifyGuildRoleGroup(JsonNode parameters) {
        return modifyGuildRoleGroup(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 修改身份组，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage modify-guild-role-group 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult modifyGuildRoleGroup(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "modify-guild-role-group", parameters, options);
    }

    /**
     * 向身份组添加成员
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.add-role-members}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #addRoleMembers(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult addRoleMembers(JsonNode parameters) {
        return addRoleMembers(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 向身份组添加成员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage add-role-members 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult addRoleMembers(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "add-role-members", parameters, options);
    }

    /**
     * 从身份组移除成员
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.remove-role-members}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #removeRoleMembers(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult removeRoleMembers(JsonNode parameters) {
        return removeRoleMembers(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 从身份组移除成员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage remove-role-members 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult removeRoleMembers(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "remove-role-members", parameters, options);
    }

    /**
     * 设置超级管理员
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.add-admin}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #addAdmin(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult addAdmin(JsonNode parameters) {
        return addAdmin(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 设置超级管理员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage add-admin 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult addAdmin(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "add-admin", parameters, options);
    }

    /**
     * 移除超级管理员
     * 涉及删除等需 --yes 的操作时，使用带 options 的重载传入 CONFIRMED。
     *
     * @param parameters 命令 JSON 参数，结构见 {@link top.yzljc.sakuraba_ema.ChannelSystem#schema(String)} 的 {@code manage.remove-admin}
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     * @see #removeAdmin(JsonNode, ChannelCliOptions)
     */
    private static ChannelCliResult removeAdmin(JsonNode parameters) {
        return removeAdmin(parameters, ChannelCliOptions.DEFAULT);
    }

    /**
     * 移除超级管理员，支持确认执行与预演选项
     *
     * @param parameters 命令 JSON 参数，同 manage remove-admin 的标准输入
     * @param options 执行选项，DEFAULT 按 CLI 默认执行，CONFIRMED 添加 --yes，DRY_RUN 添加 --dry-run
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 未启用、已关闭或进程执行异常
     */
    private static ChannelCliResult removeAdmin(JsonNode parameters, ChannelCliOptions options) {
        return ChannelCalls.client().execute("manage", "remove-admin", parameters, options);
    }

    /**
     * 创建身份组，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param name 身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult createGuildRoleGroup(String guildId, String name) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (name != null) parameters.put("name", name);
        return createGuildRoleGroup(parameters);
    }

    /**
     * 修改身份组，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param roleId 身份组 ID
     * @param name 新的身份组名称（最多 30 个字符，中文算 2 个、英文数字算 1 个）
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult modifyGuildRoleGroup(String guildId, String roleId, String name) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (roleId != null) parameters.put("role_id", roleId);
        if (name != null) parameters.put("name", name);
        return modifyGuildRoleGroup(parameters);
    }

    /**
     * 向身份组添加成员，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult addRoleMembers(String guildId, String roleId, List<String> tinyIds) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (roleId != null) parameters.put("role_id", roleId);
        if (tinyIds != null) {
            var values = parameters.putArray("tiny_ids");
            tinyIds.forEach(values::add);
        }
        return addRoleMembers(parameters);
    }

    /**
     * 从身份组移除成员，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param confirmed 是否向 CLI 传入 --yes
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeRoleMembers(String guildId, String roleId, List<String> tinyIds, boolean confirmed) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (roleId != null) parameters.put("role_id", roleId);
        if (tinyIds != null) {
            var values = parameters.putArray("tiny_ids");
            tinyIds.forEach(values::add);
        }
        return removeRoleMembers(parameters, confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
    }

    /**
     * 设置超级管理员，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult addAdmin(String guildId, List<String> tinyIds) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (tinyIds != null) {
            var values = parameters.putArray("tiny_ids");
            tinyIds.forEach(values::add);
        }
        return addAdmin(parameters);
    }

    /**
     * 移除超级管理员，使用直接参数调用
     *
     * @param guildId 腾讯频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @param confirmed 是否向 CLI 传入 --yes
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeAdmin(String guildId, List<String> tinyIds, boolean confirmed) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (guildId != null) parameters.put("guild_id", guildId);
        if (tinyIds != null) {
            var values = parameters.putArray("tiny_ids");
            tinyIds.forEach(values::add);
        }
        return removeAdmin(parameters, confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT);
    }

    /**
     * 创建身份组，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return createGuildRoleGroup(parameters, options);
    }

    /**
     * 修改身份组，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return modifyGuildRoleGroup(parameters, options);
    }

    /**
     * 向身份组添加成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return addRoleMembers(parameters, options);
    }

    /**
     * 从身份组移除成员
     *
     * @param guildId 腾讯频道 ID
     * @param roleId 身份组 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeRoleMembers(String guildId, String roleId, java.util.List<String> tinyIds) {
        return removeRoleMembers(guildId, roleId, tinyIds, ChannelCliOptions.DEFAULT);
    }

    /**
     * 从身份组移除成员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return removeRoleMembers(parameters, options);
    }

    /**
     * 设置超级管理员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return addAdmin(parameters, options);
    }

    /**
     * 移除超级管理员
     *
     * @param guildId 腾讯频道 ID
     * @param tinyIds 成员 Tiny ID 列表
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeAdmin(String guildId, java.util.List<String> tinyIds) {
        return removeAdmin(guildId, tinyIds, ChannelCliOptions.DEFAULT);
    }

    /**
     * 移除超级管理员，支持确认与预演
     *
     * @param guildId 腾讯频道 ID
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
        return removeAdmin(parameters, options);
    }

}
