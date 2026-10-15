package top.yzljc.atricore.chat.official;

import top.yzljc.atricore.AtriProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.ImageType;
import top.yzljc.atricore.platform.qq.FileType;
import top.yzljc.atricore.platform.qq.TokenManager;
import top.yzljc.atricore.service.request.HttpService;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
final class OfficialMediaUploader {

    private static final String UPLOAD_LIMIT_MESSAGE =
            "在查询数据时出现错误：服务器上行被限导致请求超时，请稍后重试，如持续发生请向开发者报告此问题！";

    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper;
    private final MessageBodyFactory bodyFactory;

    public OfficialMediaUploader(TokenManager tokenManager, ObjectMapper objectMapper, MessageBodyFactory bodyFactory) {
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.bodyFactory = bodyFactory;
    }

    public MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel,
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
    public MessageBody buildImageRequest(String uploadUrl, ImageType type, String value, String logLabel,
                                         RT rt, String content, String refIdx) {
        MessageBody request = buildImageRequest(uploadUrl, type, value, logLabel, rt, content);
        if (request != null && refIdx != null && rt != null
                && request.getMsgId() == null && request.getEventId() == null) {
            request = bodyFactory.replyText(rt, request.getContent());
        }
        return bodyFactory.withReference(request, refIdx);
    }

    public MessageBody buildFileRequest(String uploadUrl, FileType fileType, String value, String logLabel, RT rt) {
        return buildFileRequest(uploadUrl, fileType, value, logLabel, rt, false);
    }

    /**
     * @param uploadUrl 群聊或单聊的 files 接口地址
     * @param fileType  文件类型
     * @param file      本地文件；上传完成前不得修改或删除
     * @param logLabel  日志场景
     * @param rt        回复来源，主动消息传入 null
     * @return 媒体消息体，上传失败时返回 null
     */
    public MessageBody buildFileRequest(String uploadUrl, FileType fileType, Path file, String logLabel, RT rt) {
        if (ChatService.isEmergencyPaused()) return pausedMediaFallback(uploadUrl, logLabel, rt);
        try {
            String filename = file.getFileName().toString();
            JsonNode uploaded = transfer(logLabel).upload(uploadUrl, fileType, file, filename);
            MessageBody request = bodyFactory.media(fileInfo(uploaded), rt);
            var attachments = objectMapper.createArrayNode();
            var attachment = attachments.addObject();
            attachment.put("filename", filename);
            attachment.put("content_type", switch (fileType) {
                case IMAGE -> guessImageContentType(filename);
                case AUDIO -> "voice";
                case VIDEO -> "video/mp4";
                case FILE -> "file";
            });
            if (uploaded.hasNonNull("raw_url")) attachment.put("url", uploaded.get("raw_url").asText());
            request.setRecordAttachments(attachments.toString());
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("{}本地文件上传失败: {}", logLabel, e.getMessage());
            return null;
        }
    }

    public MessageBody buildFileRequest(String uploadUrl, FileType fileType, String value, String logLabel, RT rt,
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
    public MessageBody buildMaintenanceImageRequest(String uploadUrl, RT rt, String logLabel) {
        java.util.Objects.requireNonNull(rt, "维护图片必须携带被动回复来源");
        var image = ChatService.emergencyPausedMessage();
        if (image == null) return null;
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
        if (type == ImageType.BASE64) {
            try {
                String base64 = OfficialFileTransfer.normalizeBase64(value);
                String filename = filenameForImage(type, value, guessBase64ImageContentType(base64));
                return fileInfo(transfer(logLabel).uploadBase64(uploadUrl, FileType.IMAGE, value, filename));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("{}图片数据上传失败: {}", logLabel, e.getMessage());
                return null;
            }
        }
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
        try {
            return fileInfo(postUpload(uploadUrl, uploadData, logLabel));
        } catch (QQMessageSendException e) {
            if (requireMedia) throw e;
            log.error("{}上传失败: {}", logLabel, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("{}上传异常", logLabel, e);
            return null;
        }
    }

    private OfficialFileTransfer transfer(String logLabel) {
        return new OfficialFileTransfer((url, data) -> postUpload(url, data, logLabel),
                (file, part, timeout) -> putPart(file, part, timeout, logLabel));
    }

    private String fileInfo(JsonNode response) {
        String value = response.path("file_info").asText("");
        if (value.isBlank()) {
            throw QQMessageSendException.fromResponse(objectMapper, response.toString(), "上传未返回 file_info");
        }
        return value;
    }

    private JsonNode postUpload(String url, Map<String, Object> data, String logLabel)
            throws IOException, InterruptedException {
        return postUpload(url, data, logLabel, "file_info");
    }

    JsonNode postUpload(String url, Map<String, Object> data, String logLabel, String resultField)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String json = objectMapper.writeValueAsString(data);
        Map<String, Object> summary = new HashMap<>(data);
        if (summary.containsKey("file_data")) {
            summary.put("file_data", "[Base64 数据，长度 " + ((String) data.get("file_data")).length() + "]");
        }
        String loggedJson = objectMapper.writeValueAsString(summary);
        String scene = logLabel + "上传";
        String traceId = AtriProvider.getInstance().getLogRecorder().recordSend(scene, "POST", url, loggedJson);
        Integer status = null;
        String responseBody = null;
        try {
            var response = HttpService.postJsonDetailed(url, json, "Authorization", "QQBot " + tokenManager.getAccessToken());
            status = response.status();
            responseBody = response.body();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (status < 200 || status >= 300) {
                throw QQMessageSendException.fromResponse(objectMapper, responseBody, "上传失败，HTTP " + status);
            }
            JsonNode result = responseBody == null || responseBody.isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(responseBody);
            if (result == null || !result.isObject()) throw new IOException("上传响应格式无效");
            if (result.path("code").asLong(0) != 0 || result.path("err_code").asLong(0) != 0) {
                throw QQMessageSendException.fromResponse(objectMapper, responseBody, "上传失败");
            }
            if (url.endsWith("/files") && result.path(resultField).asText("").isBlank()) {
                throw QQMessageSendException.fromResponse(objectMapper, responseBody, "上传未返回 " + resultField);
            }
            AtriProvider.getInstance().getLogRecorder().recordResponse(traceId, scene, "POST", url, loggedJson, status, responseBody);
            return result;
        } catch (IOException | InterruptedException | RuntimeException e) {
            AtriProvider.getInstance().getLogRecorder().recordError(traceId, scene, "POST", url, loggedJson, status, responseBody, e.getMessage());
            throw e;
        }
    }

    private int putPart(Path file, OfficialFileTransfer.Part part, Duration timeout, String logLabel)
            throws IOException, InterruptedException {
        return putBody(part.uri(), OfficialFileTransfer.partBody(file, part), timeout, logLabel + "分片上传",
                Map.of("part_index", part.index(), "block_size", part.size()));
    }

    int putImage(URI uri, byte[] image, String scene) throws IOException, InterruptedException {
        return putBody(uri, HttpRequest.BodyPublishers.ofByteArray(image), Duration.ofMinutes(5), scene,
                Map.of("size", image.length));
    }

    private int putBody(URI uri, HttpRequest.BodyPublisher body, Duration timeout, String scene,
                        Map<String, Object> metadata) throws IOException, InterruptedException {
        String url = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        String summary = objectMapper.writeValueAsString(metadata);
        String traceId = AtriProvider.getInstance().getLogRecorder().recordSend(scene, "PUT", url, summary);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).PUT(body).build();
            var response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                AtriProvider.getInstance().getLogRecorder().recordResponse(traceId, scene, "PUT", url, summary, status, null);
            } else {
                AtriProvider.getInstance().getLogRecorder().recordError(traceId, scene, "PUT", url, summary, status, null, "分片上传失败");
            }
            return status;
        } catch (IOException | InterruptedException e) {
            AtriProvider.getInstance().getLogRecorder().recordError(traceId, scene, "PUT", url, summary, null, null, e.getMessage());
            throw e;
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
            byte[] bytes = Base64.getDecoder().decode(base64.substring(0, Math.min(base64.length(), 16)));
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
