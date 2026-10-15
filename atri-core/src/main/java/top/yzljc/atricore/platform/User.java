package top.yzljc.atricore.platform;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atricore.auth.AtriAccount;

import java.util.Optional;

/**
 * 平台用户的公共身份与权限契约，不统一各平台特有的数据字段
 *
 * @Author YZ_Ljc_
 * @ClassName User
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atricore.platform
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class User {
    protected final Platform platform;
    protected final boolean bot;
    protected final String userId;
    protected final String username;

    /**
     * 查询与当前平台用户关联的运行服务账号
     *
     * @return 关联账号，未绑定或无法查询时返回空值
     */
    public abstract Optional<AtriAccount> getAccount();

    /**
     * 检查机器人管理权限
     *
     * @return 具备机器人管理权限时返回 true，平台群内角色另行判断
     */
    public abstract boolean hasPermission();

    /**
     * 检查指定权限节点
     *
     * @param permission 权限节点
     * @return 具备管理权限或被授予该权限时返回 true，具体判断由实现提供
     */
    public abstract boolean hasPermission(String permission);

    /**
     * 查询当前平台实现支持的用户限制状态
     *
     * @return 用户受限制时返回 true；未接入限制查询的平台实现返回 false
     */
    public abstract boolean isBlocked();
}
