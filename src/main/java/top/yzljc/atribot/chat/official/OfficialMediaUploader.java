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

    MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel, String msgId) {
        return buildImageRequest(uploadUrl, type, value, logLabel, msgId, null);
    }

    MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel, String msgId, String eventId) {
        return buildImageRequest(uploadUrl, type, value, logLabel, msgId, eventId, null);
    }

    MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel,
                                  String msgId, String eventId, String content) {
        MessageBody paused = pausedMediaFallback(logLabel, msgId, eventId);
        if (paused != null || ChatService.isEmergencyPaused()) {
            return paused;
        }
        String fileInfo = uploadImageFile(uploadUrl, type, value, logLabel);
        if (fileInfo == null) {
            return bodyFactory.text(UPLOAD_LIMIT_MESSAGE);
        }
        MessageBody request = bodyFactory.media(fileInfo, msgId, eventId, content);
        request.setRecordAttachments(buildImageRecordAttachments(type, value));
        return request;
    }

    MessageBody buildFileRequest(String uploadUrl, FileType fileType, String value, String logLabel, String msgId) {
        MessageBody paused = pausedMediaFallback(logLabel, msgId, null);
        if (paused != null || ChatService.isEmergencyPaused()) {
            return paused;
        }
        String fileInfo = uploadFile(uploadUrl, fileType, value, logLabel);
        if (fileInfo == null) {
            return bodyFactory.text(UPLOAD_LIMIT_MESSAGE);
        }
        return bodyFactory.media(fileInfo, msgId);
    }

    private MessageBody pausedMediaFallback(String logLabel, String msgId, String eventId) {
        if (!ChatService.isEmergencyPaused()) {
            return null;
        }
        if (ChatService.isBlank(msgId) && ChatService.isBlank(eventId)) {
            log.warn("{}媒体主动消息已被应急暂停拦截", logLabel);
            return null;
        }
        if (!ChatService.isBlank(msgId)) {
            return bodyFactory.replyText(msgId, ChatService.emergencyPausedMessage());
        }
        return bodyFactory.eventText(eventId, ChatService.emergencyPausedMessage());
    }

    private String uploadImageFile(String uploadUrl, ImageType type, String value, String logLabel) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", 1);
        uploadData.put(type.getDataKey(), value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel);
    }

    private String uploadFile(String uploadUrl, FileType fileType, String value, String logLabel) {
        Map<String, Object> uploadData = new HashMap<>();
        uploadData.put("file_type", fileType.getValue());
        uploadData.put("url", value);
        uploadData.put("srv_send_msg", false);
        return uploadAndGetFileInfo(uploadUrl, uploadData, logLabel);
    }

    private String uploadAndGetFileInfo(String uploadUrl, Map<String, Object> uploadData, String logLabel) {
        String uploadJson = null;
        String traceId = null;
        try {
            uploadJson = objectMapper.writeValueAsString(uploadData);
            traceId = OfficialSendLogRepository.recordSend(logLabel + "上传", "POST", uploadUrl, uploadJson);
            String uploadRes = HttpService.postJsonForString(uploadUrl, uploadJson,
                    "Authorization", "QQBot " + tokenManager.getAccessToken());

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
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, logLabel + "上传", "POST", uploadUrl, uploadJson,
                    null, uploadRes);
            return resNode.get("file_info").asText();
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
