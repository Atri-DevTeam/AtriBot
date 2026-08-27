package top.yzljc.atribot.function.utils.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName DiZhenStatus
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
@Slf4j
@Deprecated(since = "不合规，不写了")
public class DiZhenStatus implements ScheduledTask, CommandExecutor {

    private static final String REQ_URL = null;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> dizhenCache = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean running = false;

//    static {
//        loadCache();
//    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.a_quarter);
    }

    @Override
    public void run() {
        synchronized (DiZhenStatus.class) {
            if (running) return;
            running = true;
        }

        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            try {
                List<DiZhenData> data = getData();
                if (data.isEmpty()) {
                    log.info("没有新的地震数据，一切安好");
                    return;
                }

                if (dizhenCache.isEmpty()) {
                    log.info("地震数据缓存为空，初始化缓存");
                    for (DiZhenData d : data) {
                        dizhenCache.add(getKey(d));
                    }
                    saveCache();
                    return;
                }

                int newCount = 0;
                for (DiZhenData d : data) {
                    String key = getKey(d);
                    if (dizhenCache.add(key)) {
                        push(d);
                        newCount++;
                        log.info("检测到新的地震数据: {}", d);
                    }
                }
                log.info("地震数据处理完成，共检测到 {} 条新数据，愿一切安好", newCount);
            } catch (Exception e) {
                log.error("处理地震数据时发生异常: {}", e.getMessage());
            } finally {
                saveCache();
                running = false;
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;
        if (!qq.hasPermission()) {
            qq.sendMessage("你没有权限执行此命令");
            return true;
        }
        this.run();
        qq.sendMessage("已手动触发地震数据检查！");
        return true;
    }

    private static void push(DiZhenData d) {

        String sb = "**地震预警**\n\n" +
                "预警时间: " + d.addTime() + "\n\n" +
                "数据更新时间: " + d.hcTime() + "\n\n" +
                "震级: " + d.level() + "\n\n" +
                "地理位置: " + d.weizhi + "\n\n";
        Markdown md = TC.md(sb +
                "维度 | 经度 | 震源深度\n" +
                ":-: | :-: | :-:\n" +
                d.weidu() + " | " + d.jingdu() + " | " + d.shendu() + "\n\n" +
                "> " + Markdown.enterCommand("/推送任务 关闭 earthquake_alert", "关闭地震预警通知"));

        for (String gid : OfficialGroups.enabledGroups("earthquake_alert")) {
            GroupChat.sendMessage(gid, md);
        }
    }

    private static void loadCache() {
        File file = new File(Properties.DI_ZHEN_DATA);
        if (!file.exists()) {
            dizhenCache.clear();
            saveCache();
            log.info("地震数据缓存文件不存在，已创建新的缓存文件");
            return;
        }

        try {
            JsonNode root = mapper.readTree(file);
            dizhenCache.clear();
            for (var node : root.get("data")) {
                dizhenCache.add(node.asText());
            }
            log.info("已加载地震数据缓存，共 {} 条记录", dizhenCache.size());
        } catch (IOException e) {
            log.error("加载地震数据缓存时发生异常: {}", e.getMessage());
        }

    }

    private static void saveCache() {
        try {
            File file = new File(Properties.DI_ZHEN_DATA);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> currentDiZhenData = new LinkedHashMap<>();
            currentDiZhenData.put("data", new ArrayList<>(dizhenCache));
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, currentDiZhenData);
        } catch (IOException e) {
            log.error("保存地震数据缓存时发生异常: {}", e.getMessage());
        }
    }

    private static String getKey(DiZhenData data) {
        return data.addTime() + ":" + data.weidu() + ":" + data.jingdu();
    }

    private static List<DiZhenData> getData() {
        var resp = HttpService.sendGetRequest(REQ_URL);
        if (resp == null || resp.isEmpty() || !resp.has("data")) {
            log.warn("获取地震数据失败，响应为空");
            return Collections.emptyList();
        }

        if (resp.path("data").isEmpty() || !resp.path("data").isArray()) {
            log.warn("获取地震数据失败，响应格式不正确");
            return Collections.emptyList();
        }

        List<DiZhenData> tmp = new LinkedList<>();
        for (var d : resp.path("data")) {
            try {
                String addTime = d.path("addtime").asText(null);
                String level = d.path("leve").asText(null);
                String weidu = d.path("weidu").asText(null);
                String jingdu = d.path("jingdu").asText(null);
                String shendu = d.path("shendu").asText(null);
                String weizhi = d.path("weizhi").asText(null);
                String hcTime = d.path("hctime").asText(null);

                tmp.add(new DiZhenData(addTime, level, weidu, jingdu, shendu, weizhi, hcTime));
            } catch (Exception e) {
                log.error("解析地震数据时发生异常: {}", e.getMessage());
            }
        }

        return tmp;
    }

    private record DiZhenData(String addTime, String level, String weidu, String jingdu, String shendu, String weizhi,
                              String hcTime) {
    }
}