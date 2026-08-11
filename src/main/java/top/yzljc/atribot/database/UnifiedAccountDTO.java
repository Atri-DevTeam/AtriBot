package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.yzljc.atribot.auth.AccountStatus;
import top.yzljc.atribot.auth.UnifiedAccount;
import top.yzljc.atribot.auth.official.UnifiedRole;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAccountDTO
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedAccountDTO {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UUID uuid;
    private String username;
    private String qqUserOpenId;
    private String qqUserUin;
    private String minecraftUuid;
    private String role;
    private List<String> permissions;
    private String status;
    private Timestamp createTime;
    private Timestamp lastUpdateTime;

    /**
     * 转回业务侧 record（role 反查枚举，时间戳转字符串）。
     */
    public UnifiedAccount toAccount() {
        return new UnifiedAccount(
                uuid,
                username,
                qqUserOpenId,
                qqUserUin,
                minecraftUuid,
                UnifiedRole.fromString(role),
                permissions,
                AccountStatus.fromString(status),
                formatTime(createTime),
                formatTime(lastUpdateTime)
        );
    }

    private static String formatTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().format(TIME_FMT);
    }
}
