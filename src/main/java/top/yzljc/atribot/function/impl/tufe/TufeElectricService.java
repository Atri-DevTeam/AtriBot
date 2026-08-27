package top.yzljc.atribot.function.impl.tufe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.TufeElecRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName TufeElectricService
 * @Created_at 2026/08/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.tufe
 */
@Slf4j
public final class TufeElectricService {
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec={room_num}&xq={school_region}";
    private static final String INFO = "![tufe #35px #34px](" + ResourcesProperties.TUFE_LOGO_IMG + ") **天津财经大学电表查询**\n\n" +
            "{placeholder_feedback}\n\n" +
            "{placeholder_data}\n\n" +
            "---\n\n" +
            "`/查询帮助` - 查看帮助\n\n" +
            "**绑定账号后可以更加便捷的查询**";

    private TufeElectricService() {
    }

    public static CheckData query(long roomNum, int schoolRegion) {
        try {
            String respJsonStr = HttpService.getRequestStr(QUERY_URL
                    .replace("{room_num}", String.valueOf(roomNum))
                    .replace("{school_region}", String.valueOf(schoolRegion)));
            JsonNode respJson;
            try {
                respJson = new ObjectMapper().readTree(respJsonStr);
            } catch (Exception e) {
                log.warn("解析电表查询结果失败，返回内容：{}", respJsonStr);
                return unavailableData();
            }

            if (respJson == null) {
                log.warn("返回内容无法解析为JSON对象");
                return unavailableData();
            }

            String rec = respJson.path("rec").asText();
            String rsmd = respJson.path("rsmd").asText();
            String rsfd = respJson.path("rsfd").asText();
            String rljd = respJson.path("rljd").asText();
            String rtzd = respJson.path("rtzd").asText();
            String rgzzt = decodeUnicode(respJson.path("rgzzt").asText());

            log.info("电表数据查询 => {}", rec);
            return new CheckData(rec, rsmd, rsfd, rljd, rtzd, rgzzt);
        } catch (Exception e) {
            log.warn("查询异常：{}", e.getMessage());
            return unavailableData();
        }
    }

    public static String formatQueryResult(CheckData checkData, int type) {
        return "电表号：`" + checkData.rec() + "`\n" +
                "电表类型：`" + (type == 0 ? "宿舍电表" : "空调电表") + "`\n" +
                "剩余免费电量：`" + checkData.rsmd() + "` 度\n" +
                "剩余收费电量：`" + checkData.rsfd() + "` 度\n" +
                "累计电量：`" + checkData.rljd() + "` 度\n" +
                "透支电量：`" + checkData.rtzd() + "` 度\n" +
                "当前工作状态：`" + checkData.rgzzt() + "`";
    }

    public static String formatInfo(String feedback, String queryResult) {
        return INFO.replace("{placeholder_feedback}", feedback).replace("{placeholder_data}", queryResult);
    }

    public static Object getKeyboard(String userId) {
        ElecDTO dormBind = TufeElecRepository.getDataByOpenIdAndType(userId, 0);
        ElecDTO airConBind = TufeElecRepository.getDataByOpenIdAndType(userId, 1);
        List<List<Button>> layout = new ArrayList<>();
        List<Button> bindButtons = new ArrayList<>();

        if (dormBind != null) {
            bindButtons.add(new Button("c1", "宿舍电表", "/宿舍电表", false, ButtonStyle.GRAY, ButtonType.COMMAND));
        }
        if (airConBind != null) {
            bindButtons.add(new Button("c2", "空调电表", "/空调电表", false, ButtonStyle.GRAY, ButtonType.COMMAND));
        }
        if (!bindButtons.isEmpty()) {
            layout.add(bindButtons);
        }
        layout.add(List.of(new Button("c4", "帮助信息", "/查询帮助", false, ButtonStyle.BLUE, ButtonType.COMMAND)));
        return TC.keyboard(layout);
    }

    private static CheckData unavailableData() {
        return new CheckData("-", "-", "-", "-", "-", "学校服务器未响应数据");
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

    public record CheckData(String rec, String rsmd, String rsfd, String rljd, String rtzd, String rgzzt) {
    }
}
