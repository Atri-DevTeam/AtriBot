package top.yzljc.atribot.function.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGuildAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGuildDirectMessageCreateEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.notify.NotificationService;
import top.yzljc.atribot.utils.tools.Alert;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName Feedback
 * @Created_at 2026/05/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
@Slf4j
public class Feedback implements CommandExecutor, Listener {

    private static final Pattern contentFormat = Pattern.compile("\\[CQ:[^\\]]*\\]");
    private static final String SOURCE = "feedback";
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender) && !(sender instanceof QQGuildCommandSender)) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("请提供反馈或建议的内容，用法：/feedback <反馈内容>");
            return true;
        }

        String firstArg = args[0].toLowerCase();

        if (firstArg.equals("list") || firstArg.equals("reply")) {
            return handleAdminCommand(sender, args);
        }

        return handleSubmitFeedback(sender, args);
    }

    private boolean handleSubmitFeedback(CommandSender sender, String[] args) {
        String platformName;
        String groupId;
        if (sender instanceof QQCommandSender qq) {
            platformName = qq.getPlatform().name();
            groupId = qq.getGroupId();
        } else if (sender instanceof QQGuildCommandSender guildSender) {
            platformName = guildSender.getPlatform().name();
            groupId = guildSender.getGuildId();
        } else {
            platformName = null;
            groupId = null;
        }
        String userId = sender.getUserId();
        String content = String.join(" ", args);

        // 检查 CQ 码
        Matcher check = contentFormat.matcher(content);
        if (check.find()) {
            sender.sendMessage("反馈内容必须为纯文本！");
            return true;
        }

        // 检查是否有未回复的反馈
        if (FeedbackRepository.hasPendingFeedback(userId)) {
            sender.sendMessage("你已经提交过反馈了哦，请先等待上一条反馈被受理！");
            return true;
        }

        // 构建 DTO
        FeedbackDTO feedback = new FeedbackDTO();
        feedback.setPlatform(platformName);
        feedback.setUserId(userId);
        feedback.setUsername(sender.getUsername());
        feedback.setGroupId(groupId);
        feedback.setSubmitContent(content);
        feedback.setCreateTime(new Timestamp(System.currentTimeMillis()));

        String id = FeedbackRepository.insert(feedback);
        if (id != null) {
            sender.sendMessage("反馈已收到！我们会尽快处理的~\n反馈编号: " + id.substring(0, 8) + "...");

            // 发送通知到调试群
            String alertStr = "收到反馈: " + content + " 来自用户: " + sender.getUsername() +
                    " (" + platformName + ": " + userId + ")" +
                    (groupId != null ? " 群聊: " + groupId : "");
            Alert.notify(alertStr);
        } else {
            sender.sendMessage("反馈提交失败了呢，请稍后再试！");
        }

        return true;
    }

    // ==================== 管理员命令 ====================

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission()) {
            return false;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            return handleList(sender, page);
        }

        if (subCommand.equals("reply")) {
            if (args.length < 3) {
                sender.sendMessage("用法: /feedback reply <反馈ID前缀> <回复内容> [--hidden]");
                return true;
            }
            String idPrefix = args[1];
            boolean isHidden = false;
            // 检查最后一个参数是否是 --hidden
            int contentEnd = args.length;
            if (args[args.length - 1].equalsIgnoreCase("--hidden")) {
                isHidden = true;
                contentEnd = args.length - 1;
            }
            String replyContent = String.join(" ", java.util.Arrays.copyOfRange(args, 2, contentEnd));
            return handleReply(sender, idPrefix, replyContent, isHidden);
        }

        return false;
    }

    private boolean handleList(CommandSender sender, int page) {
        int total = FeedbackRepository.countUnreplied();
        if (total == 0) {
            sender.sendMessage("没有待回复的反馈喵~");
            return true;
        }

        List<FeedbackDTO> list = FeedbackRepository.findUnrepliedPaginated(page, PAGE_SIZE);
        if (list.isEmpty()) {
            sender.sendMessage("第 " + page + " 页没有数据，共 " + total + " 条待回复反馈");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("待回复反馈列表 (第 ").append(page).append(" 页，共 ").append(total).append(" 条)\n");
        sb.append("━━━━━━━━━━━━━━\n");

        for (FeedbackDTO fb : list) {
            sb.append("ID: `").append(fb.getId(), 0, 8).append("`\n");
            sb.append("平台: ").append(fb.getPlatform()).append(" | 用户: ").append(fb.getUsername());
            sb.append(" (").append(fb.getUserId()).append(")\n");
            if (fb.getGroupId() != null) {
                sb.append("群聊: ").append(fb.getGroupId()).append("\n");
            }
            sb.append("时间: ").append(formatTime(fb.getCreateTime())).append("\n");
            sb.append("内容: ").append(fb.getSubmitContent()).append("\n");
            sb.append("━━━━━━━━━━━━━━\n");
        }

        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        sb.append("回复: /feedback reply <ID前缀> <回复内容> [--hidden]");
        if (page < totalPages) {
            sb.append("\n下一页: /feedback list ").append(page + 1);
        }

        if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(TC.md(sb.toString()));
        } else {
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleReply(CommandSender sender, String idPrefix, String replyContent, boolean isHidden) {
        if (replyContent.isBlank()) {
            sender.sendMessage("回复内容不能为空！");
            return true;
        }

        // 通过前缀查找反馈
        FeedbackDTO feedback = FeedbackRepository.findByIdPrefix(idPrefix);
        if (feedback == null) {
            sender.sendMessage("找不到匹配的反馈: " + idPrefix);
            return true;
        }

        boolean isReReply = feedback.getReplyContent() != null;

        boolean success = FeedbackRepository.reply(feedback.getId(), replyContent, isHidden);
        if (success) {
            boolean pushed = dispatchReply(feedback.getId());
            String msg = (isReReply ? "重新回复成功！" : "回复成功！") +
                    "反馈编号: " + feedback.getId().substring(0, 8) + "...";
            if (isHidden) {
                msg += "\n已标记为隐藏原始内容";
            }
            msg += pushed ? "\n已主动推送给用户" : "\n用户未开放主动消息，已转入被动队列";
            sender.sendMessage(msg);
            log.info("管理员 {} {}回复了反馈 {}: isHidden={}",
                    sender.getUsername(), isReReply ? "重新" : "", feedback.getId(), isHidden);
        } else {
            sender.sendMessage("回复失败，请稍后再试");
        }

        return true;
    }

    @EventHandler
    public void onGroupAtChat(OfficialGroupAtMessageCreateEvent event) {
        FeedbackDTO reply = consumePendingReply(event.getUser().getUserId());
        if (reply != null) {
            event.sendMessage(TC.md(buildReplyMessage(reply, true)));
            log.info("已送达反馈回复: userId={}, feedbackId={}", event.getUser().getUserId(), reply.getId());
        }
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        FeedbackDTO reply = consumePendingReply(event.getUser().getUserId());
        if (reply != null) {
            event.sendMessage(TC.md(buildReplyMessage(reply, true)));
            log.info("已送达反馈回复: userId={}, feedbackId={}", event.getUser().getUserId(), reply.getId());
        }
    }

    @EventHandler
    public void onPrivateChat(OfficialC2CMessageCreateEvent event) {
        FeedbackDTO reply = consumePendingReply(event.getUser().getUserId());
        if (reply != null) {
            event.sendMessage(TC.md(buildReplyMessage(reply, true)));
            log.info("已送达反馈回复: userId={}, feedbackId={}", event.getUser().getUserId(), reply.getId());
        }
    }

    @EventHandler
    public void onGuildChannelChat(OfficialGuildAtMessageCreateEvent event) {
        String channelUserId = event.getUser().getUserId();
        FeedbackDTO reply = consumePendingReply(channelUserId);
        if (reply != null) {
            event.replyMessage(buildReplyMessage(reply, false));
            log.info("已送达反馈回复(频道): userId={}, feedbackId={}", channelUserId, reply.getId());
        }
    }

    @EventHandler
    public void onGuildDirectChat(OfficialGuildDirectMessageCreateEvent event) {
        String channelUserId = event.getUser().getUserId();
        FeedbackDTO reply = consumePendingReply(channelUserId);
        if (reply != null) {
            event.replyMessage(buildReplyMessage(reply, false));
            log.info("已送达反馈回复(频道私信): userId={}, feedbackId={}", channelUserId, reply.getId());
        }
    }

    public static boolean dispatchReply(String feedbackId) {
        try {
            FeedbackDTO feedback = FeedbackRepository.findById(feedbackId);
            if (feedback == null || feedback.getReplyContent() == null) {
                return false;
            }
            if (!isOfficialPlatform(feedback.getPlatform())) {
                return false;
            }

            boolean pushed = NotificationService.notify(feedback.getPlatform(), feedback.getUserId(),
                    feedback.getGroupId(), buildReplyMessage(feedback, true), SOURCE, feedback.getId());
            FeedbackRepository.markRead(feedback.getId());
            return pushed;
        } catch (Exception e) {
            log.error("投递反馈回复失败: id={}", feedbackId, e);
            return false;
        }
    }

    private static boolean isOfficialPlatform(String platform) {
        return Platform.OFFICIAL_GROUP.name().equals(platform) || Platform.OFFICIAL_C2C.name().equals(platform);
    }

    private static FeedbackDTO consumePendingReply(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            FeedbackDTO reply = FeedbackRepository.findPendingReplyByUserId(userId);
            if (reply != null) {
                FeedbackRepository.markRead(reply.getId());
            }
            return reply;
        } catch (Exception e) {
            log.warn("消费待送达回复失败: userId={}", userId, e);
            return null;
        }
    }

    private static String buildReplyMessage(FeedbackDTO feedback, boolean markdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("**您的反馈已被受理**").append("\n\n");
        sb.append("> 反馈编号: ").append(feedback.getId(), 0, 8).append("\n");
        sb.append("> 时间: ").append(formatTime(feedback.getCreateTime())).append("\n");

        if (feedback.isHidden()) {
            sb.append("> 反馈内容：已被隐藏\n");
        } else {
            sb.append("> 反馈内容：").append(feedback.getSubmitContent()).append("\n");
        }

        sb.append("> 回复时间: ").append(formatTime(feedback.getReplyTime())).append("\n");
        sb.append("> 回复内容: ").append(feedback.getReplyContent()).append("\n\n");
        sb.append("如有任何问题欢迎继续联系我们！");

        return sb.toString();
    }

    private static String formatTime(Timestamp ts) {
        if (ts == null) return "";
        return ts.toLocalDateTime().format(TIME_FMT);
    }
}
