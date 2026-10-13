package top.yzljc.sakuraba_ema.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.sakuraba_ema.ChannelCalls;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName PostTools
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.sakuraba_ema.internal
 * @Description 频道帖子查询、发布与管理
 *
 * 保留 CLI 执行选项及详细结果，供业务封装与管理指令调用
 */
@Slf4j
public final class PostTools {

    private PostTools() {
    }

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final int IMAGE_DOWNLOAD_ATTEMPTS = 2;
    private static final HttpClient IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\]\\r\\n]*\\]\\((https?://[^\\r\\n)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );

    private static ObjectNode createParameters(String guildId, String channelId) {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        return parameters;
    }

    private static Path downloadImage(String imageUrl) throws IOException, InterruptedException {
        URI uri = URI.create(Objects.requireNonNull(imageUrl, "imageUrl"));
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("imageUrl must use http or https");
        }

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= IMAGE_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                return downloadImageOnce(uri);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt >= IMAGE_DOWNLOAD_ATTEMPTS) {
                    break;
                }
                log.warn("QQ 频道图片下载传输失败，将重试一次: host={}, error={}",
                        uri.getHost(), e.getMessage());
                Thread.sleep(Duration.ofMillis(500));
            }
        }
        throw lastFailure;
    }

    private static Path downloadImageOnce(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "AtriBot/3.2 TencentChannelImageUploader")
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response = IMAGE_HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream ignored = response.body()) {
                throw new IOException("image request returned HTTP " + response.statusCode());
            }
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > MAX_IMAGE_BYTES) {
            try (InputStream ignored = response.body()) {
                throw new IOException("image exceeds the 20 MiB download limit");
            }
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if (!normalizedType.isBlank()
                && !normalizedType.startsWith("image/")
                && !normalizedType.startsWith("application/octet-stream")) {
            try (InputStream ignored = response.body()) {
                throw new IOException("URL did not return an image, Content-Type=" + contentType);
            }
        }

        Path temporaryImage = Files.createTempFile("atribot-channel-image-", imageSuffix(uri, normalizedType));
        boolean completed = false;
        try (InputStream input = response.body();
             var output = Files.newOutputStream(temporaryImage, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("image exceeds the 20 MiB download limit");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new IOException("image response was empty");
            }
            completed = true;
            return temporaryImage;
        } finally {
            if (!completed) {
                Files.deleteIfExists(temporaryImage);
            }
        }
    }

    private static String imageSuffix(URI uri, String contentType) {
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        if (contentType.contains("gif")) return ".gif";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("bmp")) return ".bmp";

        String path = uri.getPath();
        if (path != null) {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            for (String extension : new String[]{".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}) {
                if (lowerPath.endsWith(extension)) {
                    return extension;
                }
            }
        }
        return ".img";
    }

    private static String imageHost(String imageUrl) {
        try {
            return URI.create(imageUrl).getHost();
        } catch (Exception ignored) {
            return "invalid-url";
        }
    }

    private static void deleteTemporaryImage(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理 QQ 频道发帖临时图片失败: path={}", path, e);
        }
    }

    /**
     * 发布纯文本帖子
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param text 帖子正文
     * @param options CLI 执行选项
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 执行异常或配图下载失败
     */
    public static ChannelCliResult sendMessage(String guildId, String channelId, String text, ChannelCliOptions options) {
        ObjectNode parameters = createParameters(guildId, channelId);
        parameters.put("content", text);
        return ChannelCalls.client().execute("feed", "publish-feed", parameters, options);
    }

    /**
     * 发布图片帖子，支持图片组件附带的正文
     * URL 图片先下载为临时文件，再由 CLI 上传，调用结束后清理临时文件。
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param image URL 类型图片组件，暂不支持其他 ImageType
     * @param options CLI 执行选项
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 执行异常或配图下载失败
     */
    public static ChannelCliResult sendMessage(String guildId, String channelId, ImageComponent image, ChannelCliOptions options) {
        Objects.requireNonNull(image, "image");
        if (image.getType() != ImageType.URL) {
            throw new IllegalArgumentException("Tencent Channel feed images only support ImageType.URL");
        }

        Path temporaryImage = null;
        try {
            temporaryImage = downloadImage(image.getData());
            ObjectNode parameters = createParameters(guildId, channelId);
            if (image.getText() != null && !image.getText().isBlank()) {
                parameters.put("content", image.getText());
            }
            parameters.putArray("file_paths")
                    .addObject()
                    .put("file_path", temporaryImage.toAbsolutePath().toString());
            return ChannelCalls.client().execute("feed", "publish-feed", parameters, options);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载 QQ 频道发帖图片时被中断: host={}", imageHost(image.getData()), e);
            throw new ChannelCliException("下载 QQ 频道发帖图片时被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            log.error("下载 QQ 频道发帖图片失败: host={}, error={}",
                    imageHost(image.getData()), e.getMessage(), e);
            throw new ChannelCliException("下载 QQ 频道发帖图片失败", e);
        } finally {
            deleteTemporaryImage(temporaryImage);
        }
    }

    /**
     * 发布 Markdown 帖子，自动下载正文中的配图并转换为论坛图片占位符
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param title 帖子标题
     * @param markdown Markdown 正文
     * @param options CLI 执行选项
     * @return CLI 执行结果，success 为 false 表示失败，详情见 getError()
     * @throws ChannelCliException CLI 执行异常或配图下载失败
     */
    public static ChannelCliResult sendMessage(String guildId, String channelId, String title, Markdown markdown, ChannelCliOptions options) {
        Objects.requireNonNull(markdown, "markdown");
        List<Path> temporaryImages = new ArrayList<>();
        try {
            Matcher matcher = MARKDOWN_IMAGE.matcher(markdown.getText());
            StringBuilder convertedMarkdown = new StringBuilder();
            int imageIndex = 0;
            while (matcher.find()) {
                temporaryImages.add(downloadImage(matcher.group(1)));
                matcher.appendReplacement(
                        convertedMarkdown,
                        Matcher.quoteReplacement("[(0," + imageIndex++ + ")](@img)")
                );
            }
            matcher.appendTail(convertedMarkdown);

            ObjectNode parameters = createParameters(guildId, channelId);
            parameters.put("title", title);
            parameters.put("markdown_content", convertedMarkdown.toString());
            if (!temporaryImages.isEmpty()) {
                var filePaths = parameters.putArray("file_paths");
                for (Path temporaryImage : temporaryImages) {
                    filePaths.addObject().put("file_path", temporaryImage.toAbsolutePath().toString());
                }
            }
            return ChannelCalls.client().execute("feed", "publish-feed", parameters, options);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载 QQ 频道 Markdown 配图时被中断", e);
            throw new ChannelCliException("下载 QQ 频道 Markdown 配图时被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            log.error("下载 QQ 频道 Markdown 配图失败: error={}", e.getMessage(), e);
            throw new ChannelCliException("下载 QQ 频道 Markdown 配图失败", e);
        } finally {
            for (Path temporaryImage : temporaryImages) {
                deleteTemporaryImage(temporaryImage);
            }
        }
    }

    /**
     * 获取 QQ 频道主页帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param getType 获取类型: 1=热门 2=最新，null 时沿用接口默认值
     * @param count 每页数量，null 时沿用接口默认值
     * @param feedAttachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getGuildFeeds(String guildId, Integer getType, Integer count, String feedAttachInfo, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        if (getType != null) parameters.put("get_type", getType);
        if (count != null) parameters.put("count", count);
        if (feedAttachInfo != null) parameters.put("feed_attach_info", feedAttachInfo);
        return ChannelCalls.client().execute("feed", "get-guild-feeds", parameters, options);
    }

    /**
     * 获取版块帖子列表，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param count 每页数量，null 时沿用接口默认值
     * @param feedAttachInfo 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getChannelTimelineFeeds(String guildId, String channelId, Integer count, String feedAttachInfo, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        if (count != null) parameters.put("count", count);
        if (feedAttachInfo != null) parameters.put("feed_attach_info", feedAttachInfo);
        return ChannelCalls.client().execute("feed", "get-channel-timeline-feeds", parameters, options);
    }

    /**
     * 查看帖子详情，支持确认与预演
     *
     * @param guildId QQ 频道 ID (推荐传入)
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult getPost(String guildId, String channelId, String feedId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        return ChannelCalls.client().execute("feed", "get-feed-detail", parameters, options);
    }

    /**
     * 搜索帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param query 搜索关键词
     * @param nextPageCookie 翻页令牌 (从上次返回结果获取)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult searchGuildFeeds(String guildId, String query, String nextPageCookie, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("query", query);
        if (nextPageCookie != null) parameters.put("next_page_cookie", nextPageCookie);
        return ChannelCalls.client().execute("feed", "search-guild-feeds", parameters, options);
    }

    /**
     * 获取帖子分享短链，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult sharePost(String guildId, String channelId, String feedId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        return ChannelCalls.client().execute("feed", "get-feed-share-url", parameters, options);
    }

    /**
     * 发表帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param channelId 版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (长贴必填, 有标题自动升级为长贴)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult publishText(String guildId, String channelId, String content, String title, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("content", content);
        if (title != null) parameters.put("title", title);
        return ChannelCalls.client().execute("feed", "publish-feed", parameters, options);
    }

    /**
     * 发表帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param channelId 版块 ID（普通用户模式必填；作者身份全局发帖时不传或传 0）
     * @param filePaths file-paths
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (长贴必填, 有标题自动升级为长贴)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult publishImages(String guildId, String channelId, java.util.List<String> filePaths, String content, String title, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (filePaths == null || filePaths.isEmpty() || filePaths.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("file-paths 不能为空或包含空项");
        }
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        var files = parameters.putArray("file_paths");
        filePaths.forEach(file -> files.addObject().put("file_path", file));
        parameters.put("content", content);
        if (title != null) parameters.put("title", title);
        return ChannelCalls.client().execute("feed", "publish-feed", parameters, options);
    }

    /**
     * 删除帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 创建时间戳
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult deleteFeed(String guildId, String channelId, String feedId, String createTime, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (createTime == null || createTime.isBlank()) throw new IllegalArgumentException("create-time 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("create_time", createTime);
        return ChannelCalls.client().execute("feed", "del-feed", parameters, options);
    }

    /**
     * 点赞帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult likeFeed(String guildId, String channelId, String feedId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("action", 1);
        return ChannelCalls.client().execute("feed", "do-feed-prefer", parameters, options);
    }

    /**
     * 取消帖子点赞，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unlikeFeed(String guildId, String channelId, String feedId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("action", 3);
        return ChannelCalls.client().execute("feed", "do-feed-prefer", parameters, options);
    }

    /**
     * 编辑帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 帖子创建时间戳
     * @param content 新内容（普通文本模式；与 --markdown-content 互斥）
     * @param title 新标题，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult editText(String guildId, String channelId, String feedId, String createTime, String content, String title, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (createTime == null || createTime.isBlank()) throw new IllegalArgumentException("create-time 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("create_time", createTime);
        parameters.put("content", content);
        if (title != null) parameters.put("title", title);
        return ChannelCalls.client().execute("feed", "alter-feed", parameters, options);
    }

    /**
     * 编辑帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param channelId 版块 ID
     * @param feedId 帖子 ID
     * @param createTime 帖子创建时间戳
     * @param markdownContent Markdown 正文（仅长贴; 短贴自动降级; 与 --content 互斥）
     * @param title 新标题，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult editMarkdown(String guildId, String channelId, String feedId, String createTime, String markdownContent, String title, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (createTime == null || createTime.isBlank()) throw new IllegalArgumentException("create-time 不能为空");
        if (markdownContent == null || markdownContent.isBlank()) throw new IllegalArgumentException("markdown-content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        parameters.put("create_time", createTime);
        parameters.put("markdown_content", markdownContent);
        if (title != null) parameters.put("title", title);
        return ChannelCalls.client().execute("feed", "alter-feed", parameters, options);
    }

    /**
     * 置顶帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param feedId 帖子 ID
     * @param userId 帖子发表者用户 ID
     * @param createTime 帖子创建时间戳
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult pinFeed(String guildId, String feedId, String userId, String createTime, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("user-id 不能为空");
        if (createTime == null || createTime.isBlank()) throw new IllegalArgumentException("create-time 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("feed_id", feedId);
        parameters.put("user_id", userId);
        parameters.put("create_time", createTime);
        parameters.put("action", 1);
        parameters.put("top_type", 1);
        return ChannelCalls.client().execute("feed", "top-feed", parameters, options);
    }

    /**
     * 取消置顶帖子，支持确认与预演
     *
     * @param guildId QQ 频道 ID
     * @param feedId 帖子 ID
     * @param userId 帖子发表者用户 ID
     * @param createTime 帖子创建时间戳
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult unpinFeed(String guildId, String feedId, String userId, String createTime, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("user-id 不能为空");
        if (createTime == null || createTime.isBlank()) throw new IllegalArgumentException("create-time 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("feed_id", feedId);
        parameters.put("user_id", userId);
        parameters.put("create_time", createTime);
        parameters.put("action", 2);
        parameters.put("top_type", 1);
        return ChannelCalls.client().execute("feed", "top-feed", parameters, options);
    }

    /**
     * 设置精华帖子，支持确认与预演
     *
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult markEssence(String feedId, ChannelCliOptions options) {
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("feed_id", feedId);
        parameters.put("action", 1);
        return ChannelCalls.client().execute("feed", "set-feed-essence", parameters, options);
    }

    /**
     * 取消精华帖子，支持确认与预演
     *
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult removeEssence(String feedId, ChannelCliOptions options) {
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("feed_id", feedId);
        parameters.put("action", 2);
        return ChannelCalls.client().execute("feed", "set-feed-essence", parameters, options);
    }

    /**
     * 推送精华帖通知，支持确认与预演
     *
     * @param feedId 精华帖 ID (需先设置为精华)
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult pushEssenceFeed(String feedId, ChannelCliOptions options) {
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("feed_id", feedId);
        return ChannelCalls.client().execute("feed", "push-essence-feed", parameters, options);
    }

    /**
     * 移动帖子到其他版块，支持确认与预演
     *
     * @param guildId 频道 ID
     * @param originalChannelId 帖子当前所在版块 ID
     * @param channelId 目标版块 ID
     * @param feedId 帖子 ID
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult movePost(String guildId, String originalChannelId, String channelId, String feedId, ChannelCliOptions options) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guild-id 不能为空");
        if (originalChannelId == null || originalChannelId.isBlank()) throw new IllegalArgumentException("original-channel-id 不能为空");
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channel-id 不能为空");
        if (feedId == null || feedId.isBlank()) throw new IllegalArgumentException("feed-id 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        parameters.put("original_channel_id", originalChannelId);
        parameters.put("channel_id", channelId);
        parameters.put("feed_id", feedId);
        return ChannelCalls.client().execute("feed", "move-feed", parameters, options);
    }

    /**
     * 选择频道和版块，一键发帖，支持确认与预演
     *
     * @param content 帖子内容 (普通文本模式必填; 与 --markdown-content 互斥)
     * @param title 帖子标题 (有标题自动升级为长贴)，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult quickPublishText(String content, String title, ChannelCliOptions options) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("content", content);
        if (title != null) parameters.put("title", title);
        return ChannelCalls.client().execute("feed", "quick-publish", parameters, options);
    }

    /**
     * 选择频道和版块，一键发帖，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeQuickPublish(String resumeId, String pick, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        return ChannelCalls.client().execute("feed", "quick-publish", parameters, options);
    }

    /**
     * 获取频道最新帖子详情，支持确认与预演
     *
     * @param count 帖子数量，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult latestFeedsDetail(Integer count, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (count != null) parameters.put("count", count);
        return ChannelCalls.client().execute("feed", "latest-feeds-detail", parameters, options);
    }

    /**
     * 获取频道最新帖子详情，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeLatestFeedsDetail(String resumeId, String pick, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        return ChannelCalls.client().execute("feed", "latest-feeds-detail", parameters, options);
    }

    /**
     * 获取频道热门帖子详情，支持确认与预演
     *
     * @param count 帖子数量，null 时沿用接口默认值
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult hotFeedsDetail(Integer count, ChannelCliOptions options) {
        var parameters = JsonNodeFactory.instance.objectNode();
        if (count != null) parameters.put("count", count);
        return ChannelCalls.client().execute("feed", "hot-feeds-detail", parameters, options);
    }

    /**
     * 获取频道热门帖子详情，支持确认与预演
     *
     * @param resumeId JSON 模式 resume session ID
     * @param pick resume 时的选择索引
     * @param options 执行选项，不自动确认；预演沿用现有 CLI 能力
     * @return CLI 执行结果，失败详情见 getError()
     * @throws ChannelCliException 客户端不可用或进程执行异常
     */
    public static ChannelCliResult resumeHotFeedsDetail(String resumeId, String pick, ChannelCliOptions options) {
        if (resumeId == null || resumeId.isBlank()) throw new IllegalArgumentException("resume-id 不能为空");
        if (pick == null || pick.isBlank()) throw new IllegalArgumentException("pick 不能为空");
        var parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("resume_id", resumeId);
        parameters.put("pick", pick);
        return ChannelCalls.client().execute("feed", "hot-feeds-detail", parameters, options);
    }
}
