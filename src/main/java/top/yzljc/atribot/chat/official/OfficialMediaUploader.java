package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.platform.qq.FileType;
import top.yzljc.atribot.platform.qq.TokenManager;
import top.yzljc.atribot.service.request.HttpService;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
final class OfficialMediaUploader {

    private static final String UPLOAD_LIMIT_MESSAGE =
            "在查询数据时出现错误：服务器上行被限导致请求超时，请稍后重试，如持续发生请向开发者报告此问题！";

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper;
    private final MessageBodyFactory bodyFactory;

    OfficialMediaUploader(TokenManager tokenManager, ObjectMapper objectMapper, MessageBodyFactory bodyFactory) {
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.bodyFactory = bodyFactory;
    }

    MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel,
                                  RT rt, String content) {
        if (ChatService.isEmergencyPaused()) {
            return pausedMediaFallback(uploadUrl, logLabel, rt);
        }
        String fileInfo = uploadImageFile(uploadUrl, type, value, logLabel);
        if (fileInfo == null) {
            return bodyFactory.text(UPLOAD_LIMIT_MESSAGE);
        }
        MessageBody request = bodyFactory.media(fileInfo, rt, content);
        request.setRecordAttachments(buildImageRecordAttachments(type, value));
        return request;
    }

    /**
     * 上传并组装可引用指定消息的图片，主动和被动发送均可使用
     *
     * @param uploadUrl 图片上传地址
     * @param type      图片数据类型
     * @param value     图片 URL 或 Base64 数据
     * @param logLabel  日志场景
     * @param rt        消息或事件回复来源，主动消息传入 null
     * @param content   图片附带文字
     * @param refIdx    被引用消息的索引 ID，null 表示不引用
     * @return 图片消息体；上传失败时保留原有文字回退，维护图片上传失败返回 null
     */
    MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel,
                                  RT rt, String content, String refIdx) {
        MessageBody request = buildImageRequest(uploadUrl, type, value, logLabel, rt, content);
        if (request != null && refIdx != null && rt != null
                && request.getMsgId() == null && request.getEventId() == null) {
            request = bodyFactory.replyText(rt, request.getContent());
        }
        return bodyFactory.withReference(request, refIdx);
    }

    MessageBody buildFileRequest(String uploadUrl, FileType fileType, String value, String logLabel, RT rt) {
        return buildFileRequest(uploadUrl, fileType, value, logLabel, rt, false);
    }

    MessageBody buildFileRequest(String uploadUrl, FileType fileType, String value, String logLabel, RT rt,
                                 boolean requireMedia) {
        if (requireMedia && ChatService.isEmergencyPaused()) return null;
        if (ChatService.isEmergencyPaused()) {
            return requireMedia ? null : pausedMediaFallback(uploadUrl, logLabel, rt);
        }
        String fileInfo = uploadFile(uploadUrl, fileType, value, logLabel, requireMedia);
        if (fileInfo == null) {
            if (requireMedia) return null;
            return bodyFactory.text(UPLOAD_LIMIT_MESSAGE);
        }
        MessageBody request = bodyFactory.media(fileInfo, rt);
        if (fileType == FileType.AUDIO) {
            var attachments = objectMapper.createArrayNode();
            var attachment = attachments.addObject();
            attachment.put("content_type", "voice");
            attachment.put("filename", "语音消息");
            attachment.put("url", value);
            request.setRecordAttachments(attachments.toString());
        }
        return request;
    }

    /**
     * 处理暂停状态下的媒体回复，主动发送仍被拦截
     *
     * @param uploadUrl 图片上传地址
     * @param logLabel  日志场景
     * @param rt        消息或事件回复来源，主动消息为 null
     * @return 维护图片消息，主动消息或上传失败时返回 null
     */
    private MessageBody pausedMediaFallback(String uploadUrl, String logLabel, RT rt) {
        if (rt == null) {
            log.warn("{}媒体主动消息已被应急暂停拦截", logLabel);
            return null;
        }
        return buildMaintenanceImageRequest(uploadUrl, rt, logLabel);
    }

    /**
     * 上传并组装维护图片，绕过普通媒体的暂停检查，避免重复替换
     *
     * @param uploadUrl 图片上传地址
     * @param rt        消息或事件回复来源
     * @param logLabel  日志场景
     * @return 维护图片消息，上传失败时返回 null，不回退为纯文本
     */
    MessageBody buildMaintenanceImageRequest(String uploadUrl, RT rt, String logLabel) {
        java.util.Objects.requireNonNull(rt, "维护图片必须携带被动回复来源");
        var image = ChatService.emergencyPausedMessage();
        String fileInfo = uploadImageFile(uploadUrl, image.getType(), image.getData(), logLabel + "维护图片");
        if (fileInfo == null) {
            return null;
        }
        MessageBody request = bodyFactory.media(fileInfo, rt, image.getText());
        request.setRecordAttachments(buildImageRecordAttachments(image.getType(), image.getData()));
        request.setMaintenanceReply(true);
        return request;
    }

    private String uploadImageFile(String uploadUrl, ImageType type, String value, String logLabel) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", 1);
        uploadData.put(type.getDataKey(), value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel);
    }

    private String uploadFile(String uploadUrl, FileType fileType, String value, String logLabel, boolean requireMedia) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", fileType.getValue());
        uploadData.put("url", value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel, requireMedia);
    }

    private String uploadAndGetFileInfo(String uploadUrl, Map<String, Object> uploadData, String logLabel) {
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel, false);
    }

    private String uploadAndGetFileInfo(String uploadUrl, Map<String, Object> uploadData, String logLabel,
                                        boolean requireMedia) {
        String uploadJson = null;
        String traceId = null;
        try {
            uploadJson = objectMapper.writeValueAsString(uploadData);
            traceId = OfficialSendLogRepository.recordSend(logLabel + "上传", "POST", uploadUrl, uploadJson);
            String uploadRes;
            if (requireMedia) {
                var response = HttpService.postJsonDetailed(uploadUrl, uploadJson,
                        "Authorization", "QQBot " + tokenManager.getAccessToken());
                uploadRes = response.body();
                if (response.status() < 200 || response.status() >= 300) {
                    OfficialSendLogRepository.recordError(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                            response.status(), uploadRes, "音频上传 HTTP 状态异常");
                    throw QQMessageSendException.fromResponse(objectMapper, uploadRes,
                            "音频上传失败，HTTP " + response.status());
                }
            } else {
                uploadRes = HttpService.postJsonForString(uploadUrl, uploadJson,
                        "Authorization", "QQBot " + tokenManager.getAccessToken());
            }

            if (uploadRes == null || uploadRes.isBlank()) {
                OfficialSendLogRepository.recordError(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                        null, uploadRes, "服务器返回为空");
                log.error("{}上传失败，服务器返回为空", logLabel);
                return null;
            }

            JsonNode resNode = objectMapper.readTree(uploadRes);
            if (!resNode.has("file_info")) {
                OfficialSendLogRepository.recordError(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                        null, uploadRes, "未返回 file_info");
                log.error("{}上传失败，未返回 file_info: {}", logLabel, uploadRes);
                if (requireMedia) throw QQMessageSendException.fromResponse(objectMapper, uploadRes, "音频上传未返回 file_info");
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                    null, uploadRes);
            return resNode.get("file_info").asText();
        } catch (QQMessageSendException e) {
            throw e;
        } catch (Exception e) {
            OfficialSendLogRepository.recordError(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                    null, null, "上传异常: " + e.getMessage());
            log.error("{}上传异常", logLabel, e);
            return null;
        }
    }

    private String buildImageRecordAttachments(ImageType type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String url = type == ImageType.URL ? value.trim() : toDataImageUrl(value.trim());
            if (url == null || url.isBlank()) {
                return null;
            }
            String contentType = type == ImageType.URL ? guessImageContentType(url) : guessDataImageContentType(url);
            var attachments = objectMapper.createArrayNode();
            var attachment = attachments.addObject();
            attachment.put("content_type", contentType);
            attachment.put("filename", filenameForImage(type, value, contentType));
            attachment.put("url", url);
            return objectMapper.writeValueAsString(attachments);
        } catch (Exception e) {
            log.warn("构建图片记录附件失败: {}", e.getMessage());
            return null;
        }
    }

    private String toDataImageUrl(String raw) {
        if (raw.startsWith("data:image/")) {
            return raw;
        }
        String base64 = raw.startsWith("base64://") ? raw.substring("base64://".length()) : raw;
        return "data:" + guessBase64ImageContentType(base64) + ";base64," + base64;
    }

    private String guessDataImageContentType(String url) {
        int dataStart = url.indexOf(':');
        int dataEnd = url.indexOf(';');
        if (dataStart >= 0 && dataEnd > dataStart) {
            return url.substring(dataStart + 1, dataEnd);
        }
        return "image/png";
    }

    private String guessImageContentType(String url) {
        String lower = url.toLowerCase();
        int queryIndex = lower.indexOf('?');
        if (queryIndex >= 0) {
            lower = lower.substring(0, queryIndex);
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private String guessBase64ImageContentType(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length >= 8
                    && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
                return "image/png";
            }
            if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            if (bytes.length >= 6 && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
                return "image/gif";
            }
            if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
                return "image/webp";
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "image/png";
    }

    private String filenameForImage(ImageType type, String value, String contentType) {
        if (type == ImageType.URL) {
            String clean = value;
            int queryIndex = clean.indexOf('?');
            if (queryIndex >= 0) {
                clean = clean.substring(0, queryIndex);
            }
            int slashIndex = clean.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex + 1 < clean.length()) {
                return clean.substring(slashIndex + 1);
            }
        }
        return "bot-image." + switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "png";
        };
    }
}
