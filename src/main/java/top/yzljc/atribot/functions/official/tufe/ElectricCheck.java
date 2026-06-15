package top.yzljc.atribot.functions.official.tufe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.repo.TufeElecRepository;
import top.yzljc.atribot.service.official.CommandButton;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ElectricCheck implements Listener, CommandExecutor {
    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec={room_num}&xq={school_region}";

    private static final String HELP_INFO = """
            ![tufe #35px #34px](https://www.yzljc.top/img/tufe-logo.png) **天津财经大学电表查询**
            
            **参数说明：**
            
            校区：`1` - 大学生宿舍 `2` - 校内宿舍
            
            类型：`0` - 宿舍电表 `1` - 空调电表
            
            **同一账号仅允许绑定一个宿舍电表和一个空调电表**
            
            **常用指令：**
            
            `/elec` - 信息查询
            
            `/elec bind <宿舍号> <校区> <类型>` - 绑定电表信息
            
            `/elec unbind <类型>` - 解绑电表信息
            
            **在不输入校区和类型的情况下，默认查询宿舍电表并且校区为大学生宿舍**
            
            """;

    private static final String info = "![tufe #35px #34px](https://www.yzljc.top/img/tufe-logo.png) **天津财经大学电表查询**\n\n" +
            "{placeholder_feedback}\n\n" +
            "{placeholder_data}\n\n" +
            "---\n\n" +
            "`/elec help` - 查看帮助\n\n" +
            "**绑定账号后可以更加便捷的查询**";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        ThreadManager.execute(() -> {

            if (args.length == 0) {
                handleDefaultQuery(sender, label);
                return;
            }

            if ("help".equalsIgnoreCase(args[0])) {
                sender.replyMarkdown(label, TC.md(HELP_INFO),
                        Atri.getInstance().getChatService().buildCmdKeyboard(
                                List.of(List.of(
                                                new CommandButton("c1", "绑定", "/elec bind", true, 1, 2),
                                                new CommandButton("c2", "解绑", "/elec unbind", true, 1, 2)
                                        ),
                                        List.of(
                                                new CommandButton("c3", "向开发者反馈", "/feedback <反馈内容>", true, 1, 2)
                                        ))
                        ));
                return;
            }

            if ("bind".equalsIgnoreCase(args[0])) {
                handleBind(sender, label, args);
                return;
            }

            if ("unbind".equalsIgnoreCase(args[0])) {
                handleUnbind(sender, label, args);
                return;
            }

            if ("private-check".equals(args[0])) {
                String queryResult = handlePrivateModule();
                sender.replyMarkdown(label, TC.md(info.replace("{placeholder_feedback}\n\n", "").replace("{placeholder_data}", queryResult)), getKeys("903004", "903004", "1", false, false, "/elec 903004 1 0"));
                return;
            }

            handleManualQuery(sender, label, args);
        });

        return true;
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (!GroupList.isWhitelist(event.getGroupOpenId())) return;
        for (String k : KEYWORDS_ELECTRIC) {
            if (event.getContent().equalsIgnoreCase(k)) {
                ThreadManager.execute(() -> {
                    String queryResult = handlePrivateModule();
                    event.sendMessage(TC.md(info.replace("{placeholder_feedback}\n\n", "").replace("{placeholder_data}", queryResult)), getKeys("903004", "903004", "1", false, false, "/elec 903004 1 0"));
                });
                break;
            }
        }
    }

    private static String handlePrivateModule() {
        CheckData checkData = processCheck(QUERY_URL.replace("{room_num}", "903004").replace("{school_region}", "1"));
        return "电表号：`" + checkData.rec + "`\n" +
                "电表类型：`宿舍电表`\n" +
                "剩余免费电量：`" + checkData.rsmd + "` 度\n" +
                "剩余收费电量：`" + checkData.rsfd + "` 度\n" +
                "累计电量：`" + checkData.rljd + "` 度\n" +
                "透支电量：`" + checkData.rtzd + "` 度\n" +
                "当前工作状态：`" + checkData.rgzzt + "`";
    }

    private static CheckData processCheck(String url) {
        try {
            String respJsonStr = HttpService.getRequestStr(url);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode respJson = null;
            try {
                respJson = mapper.readTree(respJsonStr);
            } catch (Exception e) {
                log.warn("解析电表查询结果失败，返回内容：{}", respJsonStr);
            }

            if (respJson != null) {
                String rec = respJson.path("rec").asText();
                String rsmd = respJson.path("rsmd").asText();
                String rsfd = respJson.path("rsfd").asText();
                String rljd = respJson.path("rljd").asText();
                String rtzd = respJson.path("rtzd").asText();
                String rgzzt = respJson.path("rgzzt").asText();

                String status = decodeUnicode(rgzzt);

                log.info("电表数据查询 => {}", rec);
                return new CheckData(rec, rsmd, rsfd, rljd, rtzd, status);
            } else {
                log.warn("返回内容无法解析为JSON对象");
                return new CheckData("-", "-", "-", "-", "-", "学校服务器未响应数据");
            }
        } catch (Exception ex) {
            log.warn("查询异常：{}", ex.getMessage());
            return new CheckData("-", "-", "-", "-", "-", "学校服务器未响应数据");
        }
    }

    private static Object getKeys(String dormRoomNum, String acRoomNum, String schoolRegion, boolean hasDorm, boolean hasAirCon, String lastCommand) {
        List<List<CommandButton>> layout = new ArrayList<>();
        List<CommandButton> bindButtons = new ArrayList<>();

        if (hasDorm) {
            bindButtons.add(new CommandButton("c1", "宿舍电表", "/elec " + dormRoomNum + " " + schoolRegion + " 0", false, 0, 2));
        }
        if (hasAirCon) {
            bindButtons.add(new CommandButton("c2", "空调电表", "/elec " + acRoomNum + " " + schoolRegion + " 1", false, 0, 2));
        }

        if (!bindButtons.isEmpty()) {
            layout.add(bindButtons);
        }

        layout.add(List.of(
                new CommandButton("c3", "再次查询", lastCommand, false, 1, 2)
        ));
        layout.add(List.of(
                new CommandButton("c4", "帮助信息", "/elec help", false, 1, 2)
        ));
        return Atri.getInstance().getChatService().buildCmdKeyboard(layout);
    }

    private static String decodeUnicode(String unicodeStr) {
        StringBuilder out = new StringBuilder();
        int len = unicodeStr.length();
        for (int i = 0; i < len; ) {
            char c = unicodeStr.charAt(i++);
            if (c == '\\' && i < len && unicodeStr.charAt(i) == 'u' && i + 4 < len) {
                String hex = unicodeStr.substring(i + 1, i + 5);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                } catch (Exception e) {
                    out.append("\\u").append(hex);
                }
                i += 5;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void handleDefaultQuery(CommandSender sender, String label) {

        ElecDTO type0 = TufeElecRepository.getDataByOpenIdAndType(sender.unionOpenId(), 0);
        ElecDTO type1 = TufeElecRepository.getDataByOpenIdAndType(sender.unionOpenId(), 1);

        int bindAmount = (type0 != null ? 1 : 0) + (type1 != null ? 1 : 0);

        String feedback;
        ElecDTO target;

        if (bindAmount == 2) {
            feedback = "当前账号有多个绑定，默认查询宿舍电表信息";
            target = type0;
        } else if (bindAmount == 1) {
            target = type0 != null ? type0 : type1;
            feedback = "当前已绑定" + (target.type() == 0 ? "宿舍" : "空调") + "电表信息，默认查询" + (target.type() == 0 ? "宿舍" : "空调") + "电表信息";
        } else {
            sender.replyMarkdown(label, TC.md(
                    info.replace("{placeholder_feedback}", "当前未绑定任何电表信息，请使用/elec help查看帮助")
                            .replace("{placeholder_data}\n\n", "")),
                    getKeys("", "", "", false, false, "/elec "));
            return;
        }

        sendQueryResult(sender, label, target.roomId(), target.schoolRegion(), target.type(), feedback, "/elec");
    }

    private void handleBind(CommandSender sender, String label, String[] args) {

        if (args.length != 4) {
            sender.reply("用法：/elec bind <宿舍号> <校区> <类型>");
            return;
        }

        try {

            long roomNum = Long.parseLong(args[1]);
            int schoolRegion = Integer.parseInt(args[2]);
            int type = Integer.parseInt(args[3]);

            if (schoolRegion != 1 && schoolRegion != 2) {
                sender.replyText(label, "未知的校区类型");
                return;
            }

            if (type != 0 && type != 1) {
                sender.reply("未知的电表类型");
                return;
            }

            CheckData checkData = processCheck(QUERY_URL.replace("{room_num}", String.valueOf(roomNum)).replace("{school_region}", String.valueOf(schoolRegion)));

            if ("-".equals(checkData.rec())) {
                sender.replyText(label, "无法查询到该电表信息");
                return;
            }

            if (TufeElecRepository.bind(sender.unionOpenId(), roomNum, schoolRegion, type)) {
                sender.replyText(label, "绑定成功");
            } else {
                sender.replyText(label, "绑定失败");
            }

        } catch (Exception e) {
            sender.replyText(label, "参数格式错误");
        }
    }

    private void handleUnbind(CommandSender sender, String label, String[] args) {

        if (args.length != 2) {
            sender.replyText(label, "用法：/elec unbind <类型>");
            return;
        }

        try {
            int type = Integer.parseInt(args[1]);
            if (type != 0 && type != 1) {
                sender.replyText(label, "未知的电表类型");
                return;
            }

            if (TufeElecRepository.unbind(sender.unionOpenId(), type)) {
                sender.replyText(label, "解绑成功");
            } else {
                sender.replyText(label, "当前未绑定该类型电表");
            }

        } catch (Exception e) {
            sender.replyText(label, "参数格式错误");
        }
    }

    private void handleManualQuery(CommandSender sender, String label, String[] args) {

        try {

            long roomNum = Long.parseLong(args[0]);
            int schoolRegion = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
            int type = args.length >= 3 ? Integer.parseInt(args[2]) : 0;

            if (schoolRegion != 1 && schoolRegion != 2) {
                sender.replyText(label, "未知的校区类型");
                return;
            }

            if (type != 0 && type != 1) {
                sender.replyText(label, "未知的电表类型");
                return;
            }

            sendQueryResult(sender, label, roomNum, schoolRegion, type, "查询成功", "/elec " + roomNum + " " + schoolRegion + " " + type);

        } catch (Exception e) {
            sender.replyText(label, "参数格式错误，请使用 /elec help 查看帮助");
        }
    }

    private void sendQueryResult(CommandSender sender, String label, long roomNum, int schoolRegion, int type, String feedback, String command) {

        CheckData checkData = processCheck(
                QUERY_URL.replace("{room_num}", String.valueOf(roomNum))
                        .replace("{school_region}", String.valueOf(schoolRegion))
        );

        String queryResult =
                "电表号：`" + checkData.rec() + "`\n" +
                        "电表类型：`" + (type == 0 ? "宿舍电表" : "空调电表") + "`\n" +
                        "剩余免费电量：`" + checkData.rsmd() + "` 度\n" +
                        "剩余收费电量：`" + checkData.rsfd() + "` 度\n" +
                        "累计电量：`" + checkData.rljd() + "` 度\n" +
                        "透支电量：`" + checkData.rtzd() + "` 度\n" +
                        "当前工作状态：`" + checkData.rgzzt() + "`";

        ElecDTO dormBind = TufeElecRepository.getDataByOpenIdAndType(sender.unionOpenId(), 0);
        ElecDTO acBind = TufeElecRepository.getDataByOpenIdAndType(sender.unionOpenId(), 1);
        boolean hasDorm = dormBind != null;
        boolean hasAirCon = acBind != null;
        String dormRoomNum = hasDorm ? String.valueOf(dormBind.roomId()) : String.valueOf(roomNum);
        String acRoomNum = hasAirCon ? String.valueOf(acBind.roomId()) : String.valueOf(roomNum);

        sender.replyMarkdown(label, TC.md(info.replace("{placeholder_feedback}", feedback).replace("{placeholder_data}", queryResult)),
                getKeys(dormRoomNum, acRoomNum, String.valueOf(schoolRegion), hasDorm, hasAirCon, command)
        );
    }

    private record CheckData(String rec, String rsmd, String rsfd, String rljd, String rtzd, String rgzzt) {
    }
}
