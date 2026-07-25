package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName InteractionType
 * @Created_at 2026/07/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum InteractionType {
    BUTTON_CLICK(11),
    CALLBACK_COMMAND(12),
    PROMPT_FEEDBACK(13),
    CLEAR_SESSION(14),
    IN_OUT_STORY(15),
    SWITCH_MODEL(16),
    USER_AUTHORIZE(18),
    GROUP_AUTHORIZE(19),
    GROUP_AUTHORIZE_STATUS(20);

    private final int interactionType;

    public static InteractionType from(int interactionType) {
        for (InteractionType type : InteractionType.values()) {
            if (type.getInteractionType() == interactionType) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown interaction type: " + interactionType);
    }
}
