package top.yzljc.atribot.chat.official.moderation;

import java.util.List;

/**
* @Author AndyOctopus
* @ClassName AiModerationVerdict
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
public record AiModerationVerdict(boolean violation, String reason, String customMessage,
                                  int level, List<String> hitWords, String provider) {

    public AiModerationVerdict(boolean violation, String reason) {
        this(violation, reason, "", 0, List.of(), null);
    }

    public AiModerationVerdict {
        reason = reason == null ? "" : reason;
        customMessage = customMessage == null ? "" : customMessage;
        hitWords = hitWords == null ? List.of() : List.copyOf(hitWords);
    }
}
