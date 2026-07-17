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
    C2C_PUSH_SWITCH(18),
    GROUP_DEV_SETTINGS(20);

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
