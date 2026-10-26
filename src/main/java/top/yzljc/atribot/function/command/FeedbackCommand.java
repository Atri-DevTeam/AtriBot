package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.command.QQGuildCommandSender;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGuildAtMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGuildDirectMessageCreateEvent;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.notify.NotificationService;
import top.yzljc.atribot.utils.tools.Alert;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;

import top.yzljc.atribot.auth.UnifiedAuthentication;

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
public class FeedbackCommand implements CommandExecutor, Listener {

    private static final Pattern contentFormat = Pattern.compile("\\[CQ:[^\\]]*\\]");
    private static final String SOURCE = "feedback";
    private static final int PAGE_SIZE = 10;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender) && !(sender instanceof QQGuildCommandSender)) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("提交或补充工单：/feedback <内容>\n关闭自己的工单：/feedback -c <工单编号>");
            return true;
        }

        String firstArg = args[0].toLowerCase();

        if (firstArg.equals("-c")) return handleClose(sender, args);

        if (firstArg.equals("list") || firstArg.equals("reply")) {
            return handleAdminCommand(sender, args);
        }

        return handleSubmitFeedback(sender, args);
    }


    private boolean handleSubmitFeedback(CommandSender sender, String[] args) {
        String groupId;
        switch (sender) {
            case QQCommandSender qq -> {
                groupId = qq.getGroupId();
            }
            case QQGuildCommandSender guildSender -> {
                groupId = guildSender.getGuildId();
            }
            case null, default -> {
                groupId = null;
            }
        }
        return submitFeedback(sender, groupId, String.join(" ", args));
    }

    /**
     * @param sender  提交者，平台和用户标识来自发送者
     * @param groupId 来源会话标识，私聊允许为 null
     * @param content 工单正文，未关闭的工单追加本次内容
     * @return 是否已处理提交
     */
    public static boolean submitFeedback(CommandSender sender, String groupId, String content) {
        String platformName = sender.getPlatform() == null ? null : sender.getPlatform().name();
        String userId = sender.getUserId();

        // 检查 CQ 码
        Matcher check = contentFormat.matcher(content);
        if (check.find()) {
            sender.sendMessage("反馈内容必须为纯文本！");
            return true;
        }

        if (content.isBlank() || content.length() > FeedbackRepository.MAX_CONTENT_LENGTH) {
            sender.sendMessage("工单内容长度须为 1—4000 个字符");
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
        if (sender instanceof QQCommandSender qq && qq.getMessage() != null) {
            feedback.setSourceMessageId(qq.getMessage().getMessageId());
            feedback.setSourceMessageTime(parseMessageTime(qq.getMessage().getTimestamp()));
        }

        var submission = FeedbackRepository.submit(feedback);
        if (submission != null) {

            feedback.setId(submission.id());

            String id = submission.id().substring(0, 8);
            if (sender instanceof QQCommandSender user) {
                if (submission.appended()) {
                    user.sendMessage(getReplyFeedbackMessage(feedback));
                } else {
                    user.sendMessage(getSubmitMessage(feedback));
                }
            } else {
                sender.sendMessage((submission.appended() ? "已补充到原工单" : "工单已提交")
                        + "\n工单编号：" + id + "\n继续补充：/feedback <内容>\n关闭工单：/feedback -c " + id);
            }

            String alertStr = (submission.appended() ? "工单补充: " : "收到工单: ") + id + "\n" + content + " 来自用户: " + sender.getUsername() +
                    " (" + platformName + ": " + userId + ")" +
                    (groupId != null ? " 群聊: " + groupId : "");
            Alert.notify(alertStr);
        } else {
            sender.sendMessage("反馈提交失败了呢，请稍后再试~");
        }

        return true;
    }

    private boolean handleClose(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("关闭工单：/feedback -c <工单编号>");
            return true;
        }
        String platform = sender.getPlatform().name();
        var ticket = FeedbackRepository.findByIdPrefix(args[1], sender.getUserId(), platform);
        if (ticket == null) {
            sender.sendMessage("无法找到匹配的用户工单，请检查编号后重试");
            return true;
        }
        var result = FeedbackRepository.close(ticket.getId(), sender.getUserId(), platform, sender.getUserId(), sender.getUsername());
        switch (result) {
            case CLOSED -> {
                if (sender instanceof QQCommandSender user) {
                    user.sendMessage(getClosedMessage(ticket));
                } else {
                    sender.sendMessage("工单已关闭，如有需要补充的内容，请重新提交新的工单！");
                }
            }
            case ALREADY_CLOSED -> sender.sendMessage("该工单已经关闭");
            case NOT_FOUND -> sender.sendMessage("获取工单信息失败，请尝试联系开发者处理！");
            case FAILED -> sender.sendMessage("工单关闭失败，请稍后重试！");
        }
        ;
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
                sender.sendMessage("用法: /feedback reply <反馈ID前缀> <回复内容>");
                return true;
            }
            String idPrefix = args[1];
            String replyContent = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            return handleReply(sender, idPrefix, replyContent);
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
            sb.append("时间: ").append(FormatTools.formatTimestamp(fb.getCreateTime(), "")).append("\n");
            sb.append("内容: ").append(fb.getSubmitContent()).append("\n");
            sb.append("━━━━━━━━━━━━━━\n");
        }

        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        sb.append("回复: /feedback reply <ID前缀> <回复内容>");
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

    private boolean handleReply(CommandSender sender, String idPrefix, String replyContent) {
        if (replyContent.isBlank() || replyContent.length() > FeedbackRepository.MAX_CONTENT_LENGTH) {
            sender.sendMessage("回复内容长度须为 1—4000 个字符");
            return true;
        }

        // 通过前缀查找反馈
        FeedbackDTO feedback = FeedbackRepository.findByIdPrefix(idPrefix);
        if (feedback == null) {
            sender.sendMessage("找不到匹配的反馈: " + idPrefix);
            return true;
        }

        boolean isReReply = feedback.getReplyContent() != null;
        String accountOpenId = sender instanceof QQGuildCommandSender guild ? guild.getUnionOpenId() : sender.getUserId();
        var account = UnifiedAuthentication.getCachedQqUserAccount(accountOpenId);
        String actorId = account == null ? sender.getPlatform().name() + ":" + sender.getUserId() : account.uuid().toString();
        String actorName = account == null ? sender.getUsername() : account.username();
        var saved = FeedbackRepository.reply(feedback.getId(), replyContent, false, actorId, actorName);
        if (saved != null) {
            boolean pushed = isOfficialPlatform(feedback.getPlatform()) && dispatchReply(saved.entry().id());
            String msg = (isReReply ? "重新回复成功！" : "回复成功！") +
                    "反馈编号: " + feedback.getId().substring(0, 8) + "...";
            msg += pushed ? "\n已发送给用户" : "\n等待被动消息补发";
            sender.sendMessage(msg);
            log.info("管理员 {} {}回复了反馈 {}",
                    sender.getUsername(), isReReply ? "重新" : "", feedback.getId());
        } else {
            sender.sendMessage("回复失败，工单可能已关闭，请刷新后重试");
        }

        return true;
    }

    @EventHandler
    public void onGroupAtChat(OfficialGroupAtMessageCreateEvent event) {
        deliverPending(event.getUser().getUserId(), Platform.OFFICIAL_GROUP,
                markdown -> event.sendMessage(markdown, false));
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        deliverPending(event.getUser().getUserId(), Platform.OFFICIAL_GROUP,
                markdown -> event.sendMessage(markdown, false));
    }

    @EventHandler
    public void onPrivateChat(OfficialC2CMessageCreateEvent event) {
        deliverPending(event.getUser().getUserId(), Platform.OFFICIAL_C2C, event::sendMessage);
    }

    @EventHandler
    public void onGuildChannelChat(OfficialGuildAtMessageCreateEvent event) {
        deliverPending(event.getUser().getUserId(), Platform.OFFICIAL_GUILD_CHANNEL, markdown -> event.replyMessage(markdown.getText()));
    }

    @EventHandler
    public void onGuildDirectChat(OfficialGuildDirectMessageCreateEvent event) {
        deliverPending(event.getUser().getUserId(), Platform.OFFICIAL_GUILD_DM, markdown -> event.replyMessage(markdown.getText()));
    }

    public static boolean dispatchReply(long entryId) {
        var reply = FeedbackRepository.claimReply(entryId, null, null);
        if (reply == null) return false;
        try {
            var feedback = reply.ticket();
            return NotificationService.notify(feedback.getPlatform(), feedback.getUserId(), feedback.getGroupId(),
                    getReplyMessage(feedback), SOURCE, feedback.getId() + ":" + entryId,
                    feedback.getSourceMessageId(), feedback.getSourceMessageTime());
        } catch (Exception e) {
            FeedbackRepository.releaseReply(entryId);
            log.error("投递工单回复失败，记录: {}", entryId, e);
            return false;
        }
    }

    static Timestamp parseMessageTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Timestamp.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isOfficialPlatform(String platform) {
        return Platform.OFFICIAL_GROUP.name().equals(platform) || Platform.OFFICIAL_C2C.name().equals(platform);
    }

    private static void deliverPending(String userId, Platform platform, Function<Markdown, String> send) {
        if (userId == null || userId.isBlank()) return;
        var reply = FeedbackRepository.claimReply(null, userId, platform.name());
        if (reply == null) return;
        try {
            if (send.apply(getReplyMessage(reply.ticket())) == null)
                FeedbackRepository.releaseReply(reply.entry().id());
        } catch (Exception e) {
            FeedbackRepository.releaseReply(reply.entry().id());
            log.warn("发送工单回复失败，记录: {}", reply.entry().id(), e);
        }
    }

    public static Markdown queuedReplyMessage(String sourceId) {
        FeedbackDTO reply = FeedbackRepository.queuedReply(sourceId);
        return reply == null ? null : getReplyMessage(reply);
    }

    private static Markdown getReplyMessage(FeedbackDTO d) {
        String shortId = "#" + d.getId().substring(0, 8);
        boolean closed = d.getStatus().equals("CLOSED");

        return TC.md("> 工单编号: " + shortId + "\n\n" +
                        "回复内容：" + d.getReplyContent() + "\n\n" +
                        "回复时间：" + FormatTools.formatTimestamp(d.getReplyTime(), "") + (closed ? "\n\n> 当前工单已被开发者关闭，如有其他问题，请提交新的工单！" : ""))
                .setKeyboard(getType(closed, d));
    }

    private static Keyboard getType(boolean closed, FeedbackDTO d) {
        if (closed) {
            return new Keyboard(List.of(
                    List.of(new Button("r-1", "提交新的工单", "/feedback ", false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND))
            ));
        } else {
            return new Keyboard(List.of(
                    List.of(new Button("r-1", "补充内容", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setReply(true),
                            new Button("r-2", "关闭工单", "/feedback -c " + d.getId(), false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND).setModal("你确定要关闭工单吗？关闭后可提交新的工单反馈。", "关闭工单", "取消"))
            ));
        }
    }

    private static Markdown getSubmitMessage(FeedbackDTO d) {
        String shortId = "#" + d.getId().substring(0, 8);

        return TC.md("> 工单编号: " + shortId + "\n\n" +
                "工单创建成功，如果您已添加机器人好友，开发者会优先通过私聊回复，若未添加，可尝试在群组内@机器人查看是否新的回复。").setKeyboard(new Keyboard(List.of(
                List.of(new Button("r-1", "补充内容", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setReply(true),
                        new Button("r-2", "关闭工单", "/feedback -c " + d.getId(), false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND).setModal("你确定要关闭工单" + shortId + "吗？关闭后可提交新的工单反馈。", "关闭工单", "取消"))
        )));
    }

    private static Markdown getClosedMessage(FeedbackDTO d) {
        String shortId = "#" + d.getId().substring(0, 8);

        return TC.md("> 工单编号: " + shortId + "\n\n" +
                "工单已关闭，若有新的问题，请重新提交工单反馈。").setKeyboard(new Keyboard(List.of(
                List.of(new Button("r-1", "提交新的工单", "/feedback ", false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND))
        )));
    }

    private static Markdown getReplyFeedbackMessage(FeedbackDTO d) {
        String shortId = "#" + d.getId().substring(0, 8);

        return TC.md("> 工单编号: " + shortId + "\n\n" +
                "信息补充成功。如果您已添加机器人好友，开发者会优先通过私聊回复，若未添加，可尝试在群组内@机器人查看是否新的回复。").setKeyboard(new Keyboard(List.of(
                List.of(new Button("r-1", "补充内容", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setReply(true),
                        new Button("r-2", "关闭工单", "/feedback -c " + d.getId(), false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND).setModal("你确定要关闭工单" + shortId + "吗？关闭后可提交新的工单反馈。", "关闭工单", "取消"))
        )));
    }
}
