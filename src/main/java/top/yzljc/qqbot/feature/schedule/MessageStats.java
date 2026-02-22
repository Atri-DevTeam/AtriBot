package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.botkits.userinfo.GetUserInfo;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.message.SensitiveWordFilter;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

public class MessageStats implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MessageStats.class);
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)]");
    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    static Settings settings = Config.getInstance();
    private static final List<Long> spyGroups = settings.getMessageSpyGroups();

    public static void autoReportAllGroups() {
        Set<Long> groups = findAllGroupsWithRecords();
        for (long groupId : groups) {
            String msg = buildGroupStatsMsg(groupId, LocalDate.now(), false, null);
            if (msg != null && !msg.isEmpty()) {
                sendAndScheduleWithdraw(groupId, msg);
            }
        }
    }

    public static Set<Long> findAllGroupsWithRecords() {
        if (spyGroups == null) {
            return new HashSet<>();
        }
        return new HashSet<>(spyGroups);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String replyMsg;
        if (args == null || args.length == 0) {
            LocalDate  targetDate = LocalDate.now();
            replyMsg = buildGroupStatsMsg(sender.getGroupId(), targetDate, false, null);
            getMessageContent(sender.getGroupId(), replyMsg);
            return true;
        }
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("y")) {
                LocalDate targetDate = LocalDate.now().minusDays(1);
                replyMsg = buildGroupStatsMsg(sender.getGroupId(), targetDate, false, null);
                getMessageContent(sender.getGroupId(), replyMsg);
            }else if (args[0].equalsIgnoreCase("overall")) {
                LocalDate targetDate = LocalDate.now();
                replyMsg = buildGroupStatsMsg(sender.getGroupId(), targetDate, true, null);
                getMessageContent(sender.getGroupId(), replyMsg);
            }else if (args[0].contains("[CQ:at,qq=")){
                Long qqAt = extractAtUser(args[0]);
                if (qqAt != null) {
                    LocalDate targetDate = LocalDate.now();
                    replyMsg = buildGroupStatsMsg(sender.getGroupId(), targetDate, false, qqAt);
                    getMessageContent(sender.getGroupId(), replyMsg);
                }
            }else{
                return false;
            }
        }
        return true;
    }

    private static void getMessageContent(long groupId,String replyMsg){
        if (replyMsg != null && !replyMsg.isEmpty()) {
            sendAndScheduleWithdraw(groupId, replyMsg);
        }
    }

    private static Long extractAtUser(String msg) {
        if (msg == null || msg.isEmpty()) return null;
        Matcher m = AT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                return Long.valueOf(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String buildGroupStatsMsg(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> statMap = statGroupSpeak(groupId, whichDay, overall, filterUserId);
        if (statMap.isEmpty()) {
            if (filterUserId != null) return "[统计] 该成员暂无发言记录";
            else return "[统计] 暂无可统计的发言记录";
        }

        String timePrefix;
        if (overall) {
            timePrefix = "历史共";
        } else if (whichDay.equals(LocalDate.now())) {
            timePrefix = "今日";
        } else {
            timePrefix = whichDay + " ";
        }

        // 单人统计
        if (filterUserId != null) {
            int count = statMap.getOrDefault(filterUserId, 0);
            String nick = fetchNickname(filterUserId);

            if (SensitiveWordFilter.containsSensitiveWord(nick)) {
                nick = null; // 触发后文的 fallback 显示QQ号
            }

            return String.format("[统计]%s：%s发言%d次",
                    nick == null ? "QQ:" + filterUserId : nick,
                    timePrefix,
                    count);
        }

        // 排行榜统计
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(statMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();

        if (overall) {
            sb.append("[历史发言总统计]\n");
        } else if (whichDay.equals(LocalDate.now())) {
            sb.append("[今日发言统计]\n");
        } else if (whichDay.equals(LocalDate.now().minusDays(1))) {
            sb.append("[昨日发言统计]\n");
        } else {
            sb.append("[").append(whichDay).append(" 发言统计]\n");
        }

        int i = 1;
        int hiddenUserCount = 0;
        long hiddenMsgCount = 0;

        long nowTime = System.currentTimeMillis();
        nicknameCache.entrySet().removeIf(entry -> nowTime - entry.getValue().time > NICKNAME_CACHE_EXPIRE);

        for (Map.Entry<Long, Integer> entry : sorted) {
            if (overall && i > 100) {
                hiddenUserCount++;
                hiddenMsgCount += entry.getValue();
                continue;
            }

            Long userId = entry.getKey();
            String nick = fetchNickname(userId);

            if (SensitiveWordFilter.containsSensitiveWord(nick)) {
                nick = null; // 强制置空，触发下方的 "QQ号:" 逻辑
            }

            sb.append(i++).append(". ")
                    .append(nick == null ? "QQ:" + userId : nick)
                    .append("：")
                    .append(entry.getValue()).append("次")
                    .append("\n");
        }

        if (hiddenUserCount > 0) {
            sb.append("此外，还有 ").append(hiddenUserCount).append(" 位群友的消息未被显示，总计 ").append(hiddenMsgCount).append(" 条\n");
        }

        return sb.toString();
    }

    private static void sendAndScheduleWithdraw(long groupId, String message) {
        try {
            Long messageId = MessageSender.sendGroupMessageGetId(groupId, message);

            if (messageId != null) {
                ThreadManager.schedule(() -> withdrawMessage(messageId), 60, TimeUnit.SECONDS);
            } else {
                MessageSender.sendGroupMessage(groupId, message);
            }
        } catch (Exception e) {
            log.warn("自动撤回发送流程异常: {}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, message);
        }
    }

    private static void withdrawMessage(long messageId) {
        PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE, "message_id", messageId);
    }

    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) {
                return cached.nick;
            }
            nicknameCache.remove(userId);

            String nick = GetUserInfo.getUserName(userId);

            if (nick == null) return null;

            nicknameCache.put(userId, new CachedNickname(nick, now));
            return nick;
        } catch (Exception e) {
            return null;
        }
    }

    private record CachedNickname(String nick, long time) {
    }

    public static Map<Long, Integer> statGroupSpeak(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> result = new HashMap<>();
        String tableName = MessageRecorder.getDynamicTableName(groupId);

        String base = "SELECT user_id, COUNT(*) as cnt FROM " + tableName + " WHERE group_id=?";
        List<Object> params = new ArrayList<>();
        params.add(groupId);

        if (!overall) {
            LocalDateTime dayStart = whichDay.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long tsBegin = dayStart.toEpochSecond(ZoneOffset.ofHours(8));
            long tsEnd = dayEnd.toEpochSecond(ZoneOffset.ofHours(8));
            base += " AND msg_time>=? AND msg_time<?";
            params.add(tsBegin);
            params.add(tsEnd);
        }
        if (filterUserId != null) {
            base += " AND user_id=?";
            params.add(filterUserId);
        }
        base += " GROUP BY user_id";

        try (Connection conn = MessageRecorder.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(base)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else if (p instanceof Long) ps.setLong(i + 1, (Long) p);
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong("user_id"), rs.getInt("cnt"));
                }
            }
        } catch (Exception e) {
            log.error("MessageStats: 统计失败 {}", e.getMessage());
        }
        return result;
    }
}