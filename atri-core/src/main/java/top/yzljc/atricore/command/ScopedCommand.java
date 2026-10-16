package top.yzljc.atricore.command;

import top.yzljc.atricore.platform.Platform;

import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName ScopedCommand
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public abstract class ScopedCommand extends CommandFeature {
    protected ScopedCommand(CommandDefinition definition) {
        super(definition);
    }

    /**
     * 提供指令的完整限定名称
     *
     * @return 包含所有者的限定名称，与注册使用的 owner:name 一致
     */
    public abstract String getQualifiedName();

    /**
     * 声明指令适用的平台
     *
     * @return 支持的平台集合，空集合表示不限制平台，不得为 null
     */
    public abstract Set<Platform> getPlatforms();

    /**
     * 判断指定平台能否使用该指令
     *
     * @param platform 来源平台，无平台来源可为 null
     * @return 未限制平台或集合包含该平台时返回 true
     */
    public boolean supports(Platform platform) {
        return getPlatforms().isEmpty() || platform != null && getPlatforms().contains(platform);
    }
}
