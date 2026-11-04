package top.yzljc.atribot.function.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.request.HttpService;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @Author YZ_Ljc_
 * @ClassName BotInfoCommand
 * @Created_at 2026/10/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public final class BotInfoCommand implements CommandExecutor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI endpoint;

    public BotInfoCommand() {
        this(URI.create("https://qun.qq.com/cgi-bin/group_pro/robot/manager/share_info"));
    }

    BotInfoCommand(URI endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender napcat)) return true;

        if (!sender.hasPermission()) return true;

        if (sender.getPlatform() != Platform.NAPCAT_GROUP && sender.getPlatform() != Platform.NAPCAT_PRIVATE)
            return true;
        if (args.length != 1 || !args[0].matches("[0-9]{1,20}")) {
            napcat.sendMessage("用法：/bot <机器人 QQ 号或 AppID>");
            return true;
        }
        String identifier = args[0];
        ThreadManager.execute(() -> {
            JsonNode robot;
            try {
                robot = lookup(identifier);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                log.warn("查询机器人信息失败，号码: {}", identifier, e);
                napcat.sendMessage("机器人信息查询失败，请稍后重试");
                return;
            }
            napcat.sendMessage(robot == null ? "机器人不存在" : render(robot));
        });
        return true;
    }

    JsonNode lookup(String identifier) throws IOException, InterruptedException {
        JsonNode robot = request("robot_uin", identifier);
        return robot != null ? robot : request("robot_appid", identifier);
    }

    private JsonNode request(String parameter, String identifier) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?" + parameter + "=" + identifier))
                .timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("机器人资料接口 HTTP " + response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        if (body == null || !body.path("retcode").isIntegralNumber())
            throw new IOException("机器人资料接口返回格式无效");
        int code = body.path("retcode").asInt();
        if (code == 10004) return null;
        if (code != 0) throw new IOException("机器人资料接口返回错误码 " + code);
        JsonNode robot = body.path("data").path("robot_data");
        if (!robot.isObject() || robot.path("appid").asText("").isBlank()
                || robot.path("robot_uin").asText("").isBlank()) {
            throw new IOException("机器人资料接口缺少机器人标识");
        }
        return robot;
    }

    private static String render(JsonNode robot) {
        boolean agent = (!robot.path("call_name").asText("").isEmpty()
                && robot.path("robot_uin").asLong(0) >= 3889001000L)
                || robot.path("robot_name").asText("").contains("已注销");
        return "机器人信息\n" +
                "名称：" + robot.path("robot_name").asText("无") + "\n" +
                "类型：" + (agent ? "智能体机器人" : "普通机器人") + "\n" +
                "QQ：" + robot.path("robot_uin").asText() + "\n" +
                "AppID：" + robot.path("appid").asText() + "\n" +
                "简介：" + robot.path("robot_desc").asText("无") + "\n" +
                "状态：" + (switch (robot.path("robot_status").asInt(-1)) {
            case 0 -> "测试中";
            case 1 -> "未审核";
            case 2 -> "已上架审核";
            case 3 -> "已封禁";
            case 4 -> "已下架/已注销";
            case 5 -> "已下架";
            default -> "未知";
        }) + "\n" +
                "允许分享：" + (robot.path("is_sharable").asBoolean(false) ? "是" : "否") + "\n" +
                "封禁：" + (robot.path("robot_ban").asBoolean(false) ? "是" : "否");
    }
}
