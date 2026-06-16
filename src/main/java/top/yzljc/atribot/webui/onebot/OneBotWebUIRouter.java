package top.yzljc.atribot.webui.onebot;

import io.javalin.Javalin;
import top.yzljc.atribot.function.general.Feedback;
import top.yzljc.atribot.webui.exception.ExceptionController;
import top.yzljc.atribot.webui.exception.FeatureNotFoundException;

public class OneBotWebUIRouter {

    public static void register(Javalin server) {
        String base = "/webui/v1/atribot/settings";
        server.get(base + "/get/{groupId}", WebUIController::getGroupSettings);
        server.post(base + "/set/{groupId}", WebUIController::setGroupSetting);
        server.get(base + "/listgroups", WebUIController::listGroups);
        server.post(base + "/fetchmessages", WebUIController::fetchMessages);
        server.post(base + "/recallmessage", WebUIController::recallMessage);
        server.post("/api/v1/response/feedback", Feedback::notifyReply);

        server.exception(FeatureNotFoundException.class, ExceptionController::handleFeatureNotFound);
    }
}
