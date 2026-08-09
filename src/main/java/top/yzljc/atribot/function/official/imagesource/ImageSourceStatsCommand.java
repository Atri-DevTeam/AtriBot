package top.yzljc.atribot.function.official.imagesource;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.impl.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.platform.Platform;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageSourceStatsCommand
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.imagesource
 */
@Slf4j
public class ImageSourceStatsCommand implements CommandExecutor {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final int PREVIEW_SIZE = 5;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) {
            return true;
        }

        boolean admin = sender.hasPermission();
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!admin) return true;
            return handlePendingList(sender);
        }

        int pending = ImageSourceRepository.countByStatus(ImageReviewStatus.PENDING.name());
        int reviewed = ImageSourceRepository.countByStatus(ImageReviewStatus.REVIEWED.name());
        int denied = ImageSourceRepository.countByStatus(ImageReviewStatus.DENIED.name());
        int total = ImageSourceRepository.countByStatus(null);

        String userId = sender.getUserId();
        int myTotal = ImageSourceRepository.countByUploader(userId, null);
        int myPending = ImageSourceRepository.countByUploader(userId, ImageReviewStatus.PENDING.name());
        int myReviewed = ImageSourceRepository.countByUploader(userId, ImageReviewStatus.REVIEWED.name());
        int myDenied = ImageSourceRepository.countByUploader(userId, ImageReviewStatus.DENIED.name());

        StringBuilder sb = new StringBuilder();
        sb.append("**图源数据统计**\n");
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("总投稿: ").append(total).append(" 张\n");
        sb.append("未审核: ").append(pending).append(" 张\n");
        sb.append("已通过: ").append(reviewed).append(" 张\n");
        sb.append("已拒绝: ").append(denied).append(" 张\n");
        sb.append("通过率: ").append(passRate(reviewed, denied)).append("\n");
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("我的投稿: ").append(myTotal).append(" 张\n");
        sb.append("　审核中: ").append(myPending).append(" | 已通过: ").append(myReviewed)
                .append(" | 已拒绝: ").append(myDenied).append("\n");

        if (admin) {
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("近 24h 投稿: ").append(ImageSourceRepository.countSince(DAY_MILLIS)).append(" 张\n");
            sb.append("待送达通知: ").append(PendingNoticeRepository.countPending()).append(" 条\n");
            if (pending > 0) {
                sb.append("查看待审: /图源 list\n");
            }
        }

        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("投稿方式: /投稿 并附带图片");

        sender.sendMessage(TC.md(sb.toString()));
        return true;
    }

    private boolean handlePendingList(CommandSender sender) {
        List<ImageSourceDTO> list = ImageSourceRepository.findPaginated(
                ImageReviewStatus.PENDING.name(), 1, PREVIEW_SIZE);
        if (list.isEmpty()) {
            sender.sendMessage("没有待审核的投稿喵~");
            return true;
        }

        int total = ImageSourceRepository.countByStatus(ImageReviewStatus.PENDING.name());

        StringBuilder sb = new StringBuilder();
        sb.append("**待审核投稿**（共 ").append(total).append(" 张，显示最新 ").append(list.size()).append(" 张）\n");
        sb.append("━━━━━━━━━━━━━━\n");
        for (ImageSourceDTO dto : list) {
            sb.append("`").append(shortId(dto.getId())).append("` ");
            sb.append(dto.getUploaderName() != null ? dto.getUploaderName() : "匿名");
            sb.append(" · ").append(dto.getWidth()).append("x").append(dto.getHeight());
            sb.append(" · ").append(formatTime(dto.getCreateTime())).append("\n");
        }
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("审核请前往 WebUI 图源管理页");

        sender.sendMessage(TC.md(sb.toString()));
        return true;
    }

    private static String passRate(int reviewed, int denied) {
        int judged = reviewed + denied;
        if (judged == 0) return "—";
        return String.format("%.1f%%", reviewed * 100.0 / judged);
    }

    private static String formatTime(Timestamp ts) {
        if (ts == null) return "-";
        return ts.toLocalDateTime().format(TIME_FMT);
    }

    private static String shortId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}