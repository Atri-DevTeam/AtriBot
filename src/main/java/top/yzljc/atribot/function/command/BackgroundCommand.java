package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;

import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.database.repo.BackgroundRepository;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundCommand
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public class BackgroundCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }
        ThreadManager.execute(() -> {
            try {
                if (args.length == 1 && "clear".equalsIgnoreCase(args[0])) {
                    BackgroundService.clear(qq.getUserId());
                    qq.sendMessage("清除自定义背景设定事宜完毕。");
                    return;
                }
                var attachments = qq.getMessage().getAttachments();
                if (attachments != null && attachments.isArray()) {
                    for (var item : attachments) {
                        if (item.path("content_type").asText().startsWith("image/") && !item.path("url").asText().isBlank()) {
                            BackgroundService.submit(qq, item.path("url").asText());
                            return;
                        }
                    }
                }
                var settings = BackgroundRepository.get(qq.getUserId());
                String status = settings.submission() == null ? "未提交" : switch (settings.submission().status()) {
                    case "PENDING" -> "等待审核";
                    case "REVIEWED" -> "已通过";
                    case "DENIED" -> "已拒绝";
                    default -> "未提交";
                };
                StringBuilder message = new StringBuilder("审核状态：").append(status);
                var submission = settings.submission();
                if (submission != null) {
                    message.append("\n审核编号：").append(submission.id());
                    message.append("\n提交时间：").append(submission.createdAt() > 0
                            ? FormatTools.formatTimestampMilli(submission.createdAt()) : "未记录");

                    if (!"PENDING".equals(submission.status())) {
                        message.append("\n审核时间：").append(submission.reviewedAt() > 0 ? FormatTools.formatTimestampMilli(submission.reviewedAt()) : "未记录");
                    }

                    if (!"PENDING".equals(submission.status()) && submission.remark() != null && !submission.remark().isBlank()) {
                        message.append("\n审核说明：").append(submission.remark());
                    }
                }
                message.append("\n\n使用 /bg [图片] 提交背景图审核，我们将在1-3个工作日内完成审核")
                        .append("\n清除背景：/bg clear");
                qq.sendMessage(message.toString());
            } catch (IllegalArgumentException e) {
                qq.sendMessage(e.getMessage());
            } catch (Exception e) {
                log.warn("背景指令执行失败: user={}", qq.getUserId(), e);
                qq.sendMessage("背景图片处理失败，请稍后重试");
            }
        });
        return true;
    }
}
