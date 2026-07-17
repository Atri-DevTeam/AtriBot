package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CAuthorizeModifyEvent
 * @Created_at 2026/07/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
public class OfficialC2CAuthorizeModifyEvent extends OfficialInteractionEvents {

    private final AuthorizeData authorizeData;
    private final OptScene optScene;
    private final Scope scope;
    private final String userOpenId;

    // 鄙人认为，传入data的原始JsonNode是最合理的做法了
    public OfficialC2CAuthorizeModifyEvent(String applicationId, String eventId, String userOpenId, String id, String scene, String timestamp, int type, int version, JsonNode data) {
        super(applicationId, eventId, id, scene, timestamp, type, version);
        this.userOpenId = userOpenId;
        this.authorizeData = new AuthorizeData(data.path("opt_scene").asText(null), data.path("scope").asText(null), data.path("switch").asBoolean(false));
        this.optScene = OptScene.from(data.path("opt_scene").asText(null));
        this.scope = Scope.from(data.path("scope").asText(null));
    }

    public record AuthorizeData(String optScene, String scope, Boolean switchData) {

        public boolean isAllowedC2CPush() {
            if (switchData == null) return false;
            return switchData;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum OptScene {
        SETTING("setting");

        private final String optScene;

        public static OptScene from(String optScene) {
            for (OptScene scene : OptScene.values()) {
                if (scene.getOptScene().equals(optScene)) {
                    return scene;
                }
            }
            return null;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum Scope {
        C2C_PUSH("c2c_push");

        private final String scope;

        public static Scope from(String scope) {
            for (Scope s : Scope.values()) {
                if (s.getScope().equals(scope)) {
                    return s;
                }
            }
            return null;
        }
    }
}
