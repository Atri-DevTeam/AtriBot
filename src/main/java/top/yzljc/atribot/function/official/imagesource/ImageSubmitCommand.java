package top.yzljc.atribot.function.official.imagesource;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.impl.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.tools.Alert;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageSubmitCommand
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.imagesource
 */
@Slf4j
public class ImageSubmitCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) {
            return true;
        }

        if (!Config.getInstance().isImageSourceEnabled()) {
            sender.sendMessage("图源投稿功能暂未开放，敬请期待~");
            return true;
        }

        List<String> imageUrls = sender.getImageUrls();
        if (imageUrls.isEmpty()) {
            sender.sendMessage("请在发送 /投稿 时一并附上图片哦，手机端可以长按聊天框输入！\n用法：/投稿 [图片]");
            return true;
        }

        String uploaderId = sender.getUserId();
        int pendingLimit = Config.getInstance().getImageSourcePendingLimit();
        if (ImageSourceRepository.countPendingByUploader(uploaderId) >= pendingLimit && !sender.hasPermission()) {
            sender.sendMessage("你还有 " + pendingLimit + " 张投稿正在等待审核，先等等这批过审再来吧~");
            return true;
        }

        String imageUrl = imageUrls.getFirst();
        JsonNode attachment = sender.getFirstImageAttachment();

        ImageSourceDTO dto = buildDTO(sender, imageUrl, attachment);

        var t = imageUrls.size();
        ThreadManager.execute(() -> process(sender, dto, t));
        return true;
    }

    private void process(CommandSender sender, ImageSourceDTO dto, int size) {
        try {
            String hash = ImageSourceClient.fetchAndHash(dto.getSourceUrl());
            if (hash == null) {
                sender.sendMessage("图片读取失败了呢，可能是链接已过期，请重新发送一次 /投稿 试试~");
                return;
            }
            dto.setHash(hash);

            ImageSourceDTO duplicate = ImageSourceRepository.findByHash(hash);
            if (duplicate != null) {
                sender.sendMessage("这张图片已经被投过稿啦（编号 " + shortId(duplicate.getId()) + "），换一张试试吧~");
                return;
            }

            // uuid 由本端生成，先落库再上报，保证 WebUI 能立即看到这条待审记录
            String id = ImageSourceRepository.insert(dto);
            if (id == null) {
                sender.sendMessage("投稿保存失败了呢，请稍后再试！");
                return;
            }
            dto.setId(id);

            ImageSourceClient.UploadResult uploadResult = ImageSourceClient.upload(dto);
            if (!uploadResult.ok()) {
                // 远端没收下，本地记录也一并回滚，否则这张图的 hash 会挡住用户重试
                ImageSourceRepository.delete(id);
                sender.sendMessage("图片上传失败了呢" + reasonSuffix(uploadResult.message()) + "，请稍后再试一次 /投稿~");
                return;
            }
            dto.setProcessedWidth(uploadResult.width());
            dto.setProcessedHeight(uploadResult.height());
            dto.setProcessedFileSize(uploadResult.fileSize());
            ImageSourceRepository.updateProcessedInfo(id, dto.getProcessedWidth(), dto.getProcessedHeight(), dto.getProcessedFileSize());

            String notify = "投稿成功！我们会尽快审核的~\n投稿编号: " + shortId(id);
            if (size > 1) {
                notify += "，单次仅能收录一张图片哦";
            }

            sender.sendMessage(notify);

            Alert.notify("收到图源投稿: 编号 " + shortId(id) +
                    " 来自用户: " + dto.getUploaderName() +
                    " (" + dto.getPlatform() + ": " + dto.getUploaderId() + ")" +
                    (dto.getGroupId() != null ? " 群聊: " + dto.getGroupId() : "") +
                    " 尺寸: " + dto.getWidth() + "x" + dto.getHeight());
        } catch (Exception e) {
            log.error("处理图源投稿失败: uploader={}", dto.getUploaderId(), e);
            try {
                sender.sendMessage("投稿处理出错了呢，请稍后再试！");
            } catch (Exception ignored) {
            }
        }
    }

    private ImageSourceDTO buildDTO(CommandSender sender, String imageUrl, JsonNode attachment) {
        ImageSourceDTO dto = new ImageSourceDTO();
        dto.setImageUuid(UUID.randomUUID().toString());
        dto.setPlatform(sender.getPlatform().name());
        dto.setUploaderId(sender.getUserId());
        dto.setUploaderName(sender.getUsername());
        dto.setGroupId(sender.getGroupId());
        dto.setSourceUrl(imageUrl);
        dto.setReviewStatus(ImageReviewStatus.PENDING.name());
        dto.setCreateTime(new Timestamp(System.currentTimeMillis()));
        if (attachment != null) {
            dto.setFileName(attachment.path("filename").asText(null));
            dto.setContentType(attachment.path("content_type").asText(null));
            dto.setWidth(attachment.path("width").asInt(0));
            dto.setHeight(attachment.path("height").asInt(0));
            dto.setFileSize(attachment.path("size").asLong(0L));
        }
        return dto;
    }

    private static String shortId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String reasonSuffix(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return "：" + reason.trim();
    }
}