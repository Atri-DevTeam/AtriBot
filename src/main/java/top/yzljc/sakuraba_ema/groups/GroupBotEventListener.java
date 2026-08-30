package top.yzljc.sakuraba_ema.groups;

/** Listener owned by one {@link GroupBotClient}; it is never globally registered. */
public interface GroupBotEventListener {

    default void onGroupMessage(GroupBotMessageEvent event) {
    }

    default void onButtonInteraction(GroupBotButtonInteractionEvent event) {
    }
}
