package top.yzljc.atribot.platform;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.auth.AtriAccount;

import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName User
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class User {
    protected final Platform platform;
    protected final boolean bot;
    protected final String userId;
    protected final String username;

    public abstract Optional<AtriAccount> getAccount();

    public abstract boolean hasPermission();

    public abstract boolean hasPermission(String permission);

    public abstract boolean isBlocked();
}
