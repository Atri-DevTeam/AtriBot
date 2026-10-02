package top.yzljc.atribot.function.command;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.impl.pic.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.function.impl.pic.ImageSourceClient;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.tools.Alert;

import java.sql.Timestamp;
import java.util.ArrayList;
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
public class PicSubmitCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        if (!Config.getInstance().isImageSourceEnabled()) {
            qq.sendMessage("图源投稿功能暂未开放，敬请期待~");
            return true;
        }

        List<ImageAttachment> images = getImageAttachments(qq.getMessage().getAttachments());
        if (images.isEmpty()) {
            qq.sendMessage("请在发送 /submit 时一并附上图片哦，手机端可以长按聊天框输入！\n用法：/submit [图片]");
            return true;
        }

        String uploaderId = qq.getUserId();
        int pendingLimit = Config.getInstance().getImageSourcePendingLimit();
        int submitCount = qq.hasPermission("atri.submit.multiple") ? images.size() : 1;
        int pendingCount = ImageSourceRepository.countPendingByUploader(uploaderId);
        if (!qq.hasPermission("atri.submit.multiple") && pendingCount + submitCount > pendingLimit) {
            qq.sendMessage("你已有 " + pendingCount + " 张投稿正在等待审核，本次投稿 " + submitCount
                    + " 张会超过 " + pendingLimit + " 张的待审上限，请减少图片或等审核后再试~");
            return true;
        }

        List<ImageSourceDTO> submissions = new ArrayList<>(submitCount);
        for (int i = 0; i < submitCount; i++) {
            ImageAttachment image = images.get(i);
            submissions.add(buildDTO(qq, image.url(), image.attachment()));
        }
        ThreadManager.execute(() -> process(qq, submissions, images.size()));
        return true;
    }

    private void process(QQCommandSender sender, List<ImageSourceDTO> submissions, int imageCount) {
        List<String> results = new ArrayList<>(submissions.size());
        List<ImageSourceDTO> successful = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            ImageSourceDTO dto = submissions.get(i);
            SubmissionResult result = processOne(dto);
            String message = result.message();
            if (submissions.size() > 1) {
                message = "第 " + (i + 1) + " 张：" + message.replace("\n", "，");
            } else if (imageCount > 1 && result.success()) {
                message += "，单次仅能收录一张图片哦";
            }
            results.add(message);
            if (result.success()) {
                successful.add(dto);
            }
        }
        try {
            sender.sendMessage(String.join("\n", results));
        } catch (Exception e) {
            log.error("发送图源投稿结果失败: uploader={}", sender.getUserId(), e);
        }
        for (ImageSourceDTO dto : successful) {
            try {
                Alert.notify("收到图源投稿: 编号 " + shortId(dto.getId()) +
                        " 来自用户: " + dto.getUploaderName() +
                        " (" + dto.getPlatform() + ": " + dto.getUploaderId() + ")" +
                        (dto.getGroupId() != null ? " 群聊: " + dto.getGroupId() : "") +
                        " 尺寸: " + dto.getWidth() + "x" + dto.getHeight());
            } catch (Exception e) {
                log.warn("发送图源投稿提醒失败: id={}", dto.getId(), e);
            }
        }
    }

    private SubmissionResult processOne(ImageSourceDTO dto) {
        try {
            String hash = ImageSourceClient.fetchAndHash(dto.getSourceUrl());
            if (hash == null) {
                return new SubmissionResult("图片读取失败了呢，可能是链接已过期，请重新发送一次 /submit 试试~", false);
            }
            dto.setHash(hash);

            ImageSourceDTO duplicate = ImageSourceRepository.findByHash(hash);
            if (duplicate != null) {
                return new SubmissionResult("这张图片已经被投过稿啦（编号 " + shortId(duplicate.getId()) + "），换一张试试吧~", false);
            }

            // uuid 由本端生成，先落库再上报，保证 WebUI 能立即看到这条待审记录
            String id = ImageSourceRepository.insert(dto);
            if (id == null) {
                return new SubmissionResult("投稿保存失败了呢，请稍后再试！", false);
            }
            dto.setId(id);

            ImageSourceClient.UploadResult uploadResult = ImageSourceClient.upload(dto);
            if (!uploadResult.ok()) {
                // 远端没收下，本地记录也一并回滚，否则这张图的 hash 会挡住用户重试
                ImageSourceRepository.delete(id);
                return new SubmissionResult("图片上传失败了呢" + reasonSuffix(uploadResult.message()) + "，请稍后再试一次 /投稿~", false);
            }
            dto.setProcessedWidth(uploadResult.width());
            dto.setProcessedHeight(uploadResult.height());
            dto.setProcessedFileSize(uploadResult.fileSize());
            ImageSourceRepository.updateProcessedInfo(id, dto.getProcessedWidth(), dto.getProcessedHeight(), dto.getProcessedFileSize());

            return new SubmissionResult("投稿成功！我们会尽快审核的~\n投稿编号: " + shortId(id), true);
        } catch (Exception e) {
            log.error("处理图源投稿失败: uploader={}", dto.getUploaderId(), e);
            return new SubmissionResult("投稿处理出错了呢，请稍后再试！", false);
        }
    }

    private ImageSourceDTO buildDTO(QQCommandSender sender, String imageUrl, JsonNode attachment) {
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

    /**
     * 从消息附件的原始 {@code attachments} 字段中筛出图片及对应元信息（命令内联解析）。
     *
     * <p>官方 Bot 的图片附件形如
     * {@code {"content_type":"image/png","filename":"...","url":"multimedia.nt.qq.com.cn/download?...","width":765,"height":160,"size":27783}}，
     * 其中 {@code url} 不带协议头且带有会过期的 rkey，这里统一补全为 https 链接。
     *
     * @return 按附件顺序排列的图片，无图片时返回空列表
     */
    private static List<ImageAttachment> getImageAttachments(JsonNode attachments) {
        if (attachments == null || !attachments.isArray()) {
            return List.of();
        }
        List<ImageAttachment> images = new ArrayList<>();
        for (JsonNode attachment : attachments) {
            String contentType = attachment.path("content_type").asText("");
            if (!contentType.startsWith("image/")) continue;
            String url = attachment.path("url").asText(null);
            if (url == null || url.isBlank()) continue;
            images.add(new ImageAttachment(url.startsWith("http") ? url : "https://" + url, attachment));
        }
        return images;
    }

    private record ImageAttachment(String url, JsonNode attachment) {}

    private record SubmissionResult(String message, boolean success) {}

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