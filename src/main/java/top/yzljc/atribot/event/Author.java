package top.yzljc.atribot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author YZ_Ljc_
 * @ClassName Author
 * @Created_at 2026/05/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.event
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Author {
    private boolean bot;
    private String id;
    private String memberOpenId;
    private String unionOpenId;
    private String username;
}