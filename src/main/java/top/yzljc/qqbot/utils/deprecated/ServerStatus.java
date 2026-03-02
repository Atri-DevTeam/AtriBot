package top.yzljc.qqbot.utils.deprecated;

import top.yzljc.qqbot.botservice.message.MessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.command.impl.Command;
import top.yzljc.qqbot.command.impl.CommandExecutor;
import top.yzljc.qqbot.command.impl.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.minecraft.ServerStatusImage;

/**
 * 无用的功能，已废弃
 */
@Deprecated(since = "2.6.1",forRemoval = true)
public class ServerStatus implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ServerStatus.class);
    private static final String SERVER_LIST = ConfigFile.SERVER_LIST.getFileName();
    private static final String ADMIN_FILE = ConfigFile.RCON_USER.getFileName();
    static Settings settings = Config.getInstance();
    private static final List<Long> adminIds = settings.getAdminUids();

    public static class ServerInfo {
        public long group_id;
        public String name;
        public String ip;
        public int port;
        public String id;
        public String server_mode;
    }

    public static List<ServerInfo> loadServerList() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(SERVER_LIST);
            List<ServerInfo> list = mapper.readValue(file, new TypeReference<List<ServerInfo>>() {});
            if (list != null) {
                for (ServerInfo si : list) {
                    if (si.server_mode == null) si.server_mode = "normal";
                }
            }
            return list;
        } catch (Exception e) {
            log.error("读取服务器列表文件时出错：{}", e.getMessage());
            return null;
        }
    }

    public static void sendReport(long groupId, String name, String ip, int port, String id, boolean isOnline) {

        if (isMaintenance(id)) {
            log.info("服务器[{}]当前为maintenance，跳过群[{}]推送", id, groupId);
            return;
        }

        File tempFile = null;
        String statusDesc = isOnline ? "在线" : "离线";

        try {
            log.info("开始构建推送消息……");

            String textContent = String.format(
                    "[!] 服务器状态更新\n服务器：%s\n地址：%s:%d\n状态：%s",
                    name, ip, port, statusDesc
            );

            File tmpDir = new File("tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }

            String fileName = String.format("status_%s_%d.png", id, System.currentTimeMillis());
            tempFile = new File(tmpDir, fileName);

            log.info("准备生成图片：{}", tempFile.getAbsolutePath());
            String ipPort = ip + ":" + port;

            ServerStatusImage.generateStatusImage(name, ipPort, statusDesc, tempFile.getAbsolutePath());

            String base64Img = null;
            if (tempFile.exists()) {
                log.info("图片生成成功，大小：{}", tempFile.length());
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                base64Img = Base64.getEncoder().encodeToString(imgBytes);
            } else {
                log.warn("图片生成失败，文件不存在，将只发送文本");
            }

            MessageSender.sendGroupMessage(groupId, textContent, base64Img);

        } catch (Exception ex) {
            log.error("推送流程异常：{}", ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                log.info("临时图片已清理");
            }
        }
    }

    private static boolean isMaintenance(String id) {
        try {
            List<ServerInfo> servers = loadServerList();
            if (servers != null) {
                for (ServerInfo s : servers) {
                    if (id.equals(s.id)) {
                        return "maintenance".equalsIgnoreCase(s.server_mode);
                    }
                }
            }
        } catch (Exception e) {
            log.error("检查server_mode出错：{}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return false;
        }
        if (!(args[1].equalsIgnoreCase("normal") || args[1].equalsIgnoreCase("maintenance"))) {
            return false;
        }else{
            String serverId = args[0];
            String mode = args[1];
            try {
                List<ServerInfo> servers = loadServerList();
                if (servers == null) {
                    sender.reply("服务器列表加载失败，无法切换模式！", false);
                    return true;
                }
                ServerInfo s = null;
                for (ServerInfo si : servers) {
                    if (serverId.equals(si.id)) {
                        s = si;
                        break;
                    }
                }
                if (s == null) {
                    sender.reply("未找到对应的服务器ID，无法切换模式！", false);
                    return true;
                }
                if (!canAuth(sender.getUserId(), sender.getGroupId(), serverId)) {
                    sender.reply("无操作权限！", false);
                    return true;
                }
                s.server_mode = mode;
                ObjectMapper mapper = new ObjectMapper();
                mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SERVER_LIST), servers);
                sender.reply("服务器 [" + s.name + "] 模式已切换为：" + mode, false);
                return true;
            } catch (Exception ex) {
                log.error("管理指令处理出错: {}", ex.getMessage());
                sender.reply("处理指令时发生错误: " + ex.getMessage(), false);
                return true;
            }
        }
    }

    private static boolean canAuth(long userId, long groupId, String serverId) {
        if (adminIds.contains(userId)) return true;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode arr = mapper.readTree(new File(ADMIN_FILE));
            for (JsonNode obj : arr) {
                if (
                        String.valueOf(userId).equals(obj.path("user").asText())
                                && String.valueOf(groupId).equals(obj.path("group").asText())
                                && serverId.equals(obj.path("server-id").asText())
                ) return true;
            }
        } catch (Exception e) {
            log.error("读取用户鉴权配置文件失败：{}", e.getMessage());
        }
        return false;
    }
}