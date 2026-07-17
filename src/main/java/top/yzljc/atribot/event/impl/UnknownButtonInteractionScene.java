package top.yzljc.atribot.event.impl;

/**
 * @Author YZ_Ljc_
 * @ClassName UnknownButtonInteractionScene
 * @Created_at 2026/07/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
public class UnknownButtonInteractionScene extends RuntimeException {
    public UnknownButtonInteractionScene(int chatType, String scene) {
        String message = "未知的按钮交互场景: chatType=" + chatType + ", scene=" + scene;
        super(message);
    }
}
