package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
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

@Slf4j
public final class ChannelPosts {

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

    public static ChannelCliResult sendMessage(String guildId, String channelId, String text) {
        ObjectNode parameters = createParameters(guildId, channelId);
        parameters.put("content", text);
        return publish(parameters);
    }

    public static ChannelCliResult sendMessage(String guildId, String channelId, ImageComponent image) {
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
            return publish(parameters);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载腾讯频道发帖图片时被中断: host={}", imageHost(image.getData()), e);
            throw new ChannelCliException("下载腾讯频道发帖图片时被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            log.error("下载腾讯频道发帖图片失败: host={}, error={}",
                    imageHost(image.getData()), e.getMessage(), e);
            throw new ChannelCliException("下载腾讯频道发帖图片失败", e);
        } finally {
            deleteTemporaryImage(temporaryImage);
        }
    }

    public static ChannelCliResult sendMessage(String guildId, String channelId,
                                               String title, Markdown markdown) {
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
            return publish(parameters);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载腾讯频道 Markdown 配图时被中断", e);
            throw new ChannelCliException("下载腾讯频道 Markdown 配图时被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            log.error("下载腾讯频道 Markdown 配图失败: error={}", e.getMessage(), e);
            throw new ChannelCliException("下载腾讯频道 Markdown 配图失败", e);
        } finally {
            for (Path temporaryImage : temporaryImages) {
                deleteTemporaryImage(temporaryImage);
            }
        }
    }

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
                log.warn("腾讯频道图片下载传输失败，将重试一次: host={}, error={}",
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
            log.warn("清理腾讯频道发帖临时图片失败: path={}", path, e);
        }
    }

    private static ChannelCliResult publish(ObjectNode parameters) {
        return Atri.getInstance()
                .getTencentChannelCliClient()
                .feed()
                .publishFeed(parameters);
    }
}
