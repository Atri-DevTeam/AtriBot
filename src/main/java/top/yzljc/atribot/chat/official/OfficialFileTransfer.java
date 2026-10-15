package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.platform.qq.FileType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialFileTransfer
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 * @Description QQ 群聊与单聊本地文件上传协议
 */
final class OfficialFileTransfer {
    static final int DIRECT_LIMIT = 5_000_000;
    static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    static final int PREFIX_SIZE = 10_002_432;

    private final JsonRequest jsonRequest;
    private final PartRequest partRequest;

    OfficialFileTransfer(JsonRequest jsonRequest, PartRequest partRequest) {
        this.jsonRequest = jsonRequest;
        this.partRequest = partRequest;
    }

    JsonNode upload(String url, FileType type, Path file, String filename) throws IOException, InterruptedException {
        long size = Files.size(file);
        checkSize(size);
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("文件名不能为空");
        if (size <= DIRECT_LIMIT) {
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) {
                bytes = input.readNBytes(DIRECT_LIMIT + 1);
            }
            if (bytes.length != size) throw new IOException("上传期间文件大小发生变化");
            return jsonRequest.post(url, Map.of("file_type", type.getValue(), "file_name", filename,
                    "file_data", Base64.getEncoder().encodeToString(bytes), "srv_send_msg", false));
        }
        return uploadParts(url, type, file, filename, size);
    }

    JsonNode uploadBase64(String url, FileType type, String value, String filename)
            throws IOException, InterruptedException {
        String data = normalizeBase64(value);
        if (data.length() > ((MAX_FILE_SIZE + 2) / 3) * 4) {
            throw new IllegalArgumentException("文件大小超过 200 MiB");
        }
        Path temporary = Files.createTempFile("qq-media-", ".upload");
        try {
            try (InputStream decoded = Base64.getDecoder().wrap(base64Input(data));
                 var output = Files.newOutputStream(temporary)) {
                decoded.transferTo(output);
            }
            return upload(url, type, temporary, filename);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String normalizeBase64(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("文件数据不能为空");
        String raw = value.trim();
        if (raw.startsWith("base64://")) return raw.substring(9);
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            if (comma < 0 || !raw.substring(0, comma).endsWith(";base64")) {
                throw new IllegalArgumentException("不支持的 Base64 数据格式");
            }
            return raw.substring(comma + 1);
        }
        return raw;
    }

    private JsonNode uploadParts(String url, FileType type, Path file, String filename, long size)
            throws IOException, InterruptedException {
        var modified = Files.getLastModifiedTime(file);
        Digests digests = digest(file);
        if (digests.size() != size) throw new IOException("上传期间文件大小发生变化");
        String baseUrl = url.substring(0, url.length() - "/files".length());
        JsonNode prepared = jsonRequest.post(baseUrl + "/upload_prepare", Map.of(
                "file_type", type.getValue(), "file_size", Long.toString(size), "file_name", filename,
                "md5", digests.md5(), "sha1", digests.sha1(), "md5_10m", digests.prefixMd5()));
        String uploadId = prepared.path("upload_id").asText("");
        if (uploadId.isBlank()) throw new IOException("预上传响应缺少 upload_id");
        List<Part> parts = parseParts(prepared, size);
        JsonNode config = prepared.path("upload_config");
        int retryTimeout = Math.clamp(config.path("retry_timeout").asInt(300), 0, 300);
        int retryDelay = Math.clamp(config.path("retry_delay").asInt(1), 1, 30);
        for (Part part : parts) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            String md5 = digestPart(file, part.offset(), part.size());
            putPart(file, part, retryTimeout, retryDelay);
            jsonRequest.post(baseUrl + "/upload_part_finish", Map.of(
                    "upload_id", uploadId, "part_index", part.index(),
                    "block_size", Long.toString(part.size()), "md5", md5));
        }
        if (Files.size(file) != size || !Files.getLastModifiedTime(file).equals(modified)) {
            throw new IOException("上传期间文件发生变化");
        }
        return jsonRequest.post(url, Map.of("file_type", type.getValue(), "file_name", filename,
                "upload_id", uploadId, "srv_send_msg", false));
    }

    static List<Part> parseParts(JsonNode response, long size) throws IOException {
        long blockSize = response.path("block_size").asLong(0);
        JsonNode nodes = response.path("parts");
        if (blockSize <= 0 || !nodes.isArray() || nodes.size() != (size - 1) / blockSize + 1) {
            throw new IOException("预上传响应的分片数量或大小无效");
        }
        List<JsonNode> sorted = new ArrayList<>();
        nodes.forEach(sorted::add);
        sorted.sort(Comparator.comparingInt(node -> node.path("index").asInt(-1)));
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            JsonNode node = sorted.get(i);
            long offset = i * blockSize;
            long length = Math.min(blockSize, size - offset);
            long declared = node.path("block_size").asLong(length);
            if (node.path("index").asInt(-1) != i || Math.min(declared, size - offset) != length) {
                throw new IOException("预上传响应的分片索引或大小无效");
            }
            URI uri;
            try {
                uri = URI.create(node.path("presigned_url").asText(""));
            } catch (IllegalArgumentException e) {
                throw new IOException("预上传响应的分片地址无效", e);
            }
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                throw new IOException("预上传响应的分片地址无效");
            }
            parts.add(new Part(i, offset, length, uri));
        }
        return parts;
    }

    private void putPart(Path file, Part part, int retryTimeout, int retryDelay)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(retryTimeout).toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            Duration timeout = Duration.ofNanos(Math.max(1, Math.min(Duration.ofSeconds(60).toNanos(), remaining)));
            if (retryTimeout == 0) timeout = Duration.ofSeconds(60);
            IOException failure;
            try {
                int status = partRequest.put(file, part, timeout);
                if (status >= 200 && status < 300) return;
                if (status != 429 && status < 500) throw new NonRetryableUploadException(status);
                failure = new IOException("分片上传失败，HTTP " + status);
            } catch (NonRetryableUploadException e) {
                throw e;
            } catch (IOException e) {
                failure = e;
            }
            if (deadline - System.nanoTime() <= Duration.ofSeconds(retryDelay).toNanos()) throw failure;
            Thread.sleep(Duration.ofSeconds(retryDelay));
        }
    }

    static HttpRequest.BodyPublisher partBody(Path file, Part part) {
        var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return openRange(file, part.offset(), part.size());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return HttpRequest.BodyPublishers.fromPublisher(publisher, part.size());
    }

    private static InputStream openRange(Path file, long offset, long length) throws IOException {
        InputStream input = Files.newInputStream(file);
        try {
            input.skipNBytes(offset);
        } catch (IOException e) {
            input.close();
            throw e;
        }
        return new InputStream() {
            private long remaining = length;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] bytes, int start, int count) throws IOException {
                if (count == 0) return 0;
                if (remaining == 0) return -1;
                int read = input.read(bytes, start, (int) Math.min(count, remaining));
                if (read < 0) throw new IOException("文件在分片范围结束前已到达末尾");
                remaining -= read;
                return read;
            }

            @Override
            public void close() throws IOException {
                input.close();
            }
        };
    }

    private static String digestPart(Path file, long offset, long length) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        try (InputStream input = openRange(file, offset, length)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) md5.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(md5.digest());
    }

    static Digests digest(Path file) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        MessageDigest sha1 = newDigest("SHA-1");
        MessageDigest prefix = newDigest("MD5");
        long size = 0;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                md5.update(buffer, 0, read);
                sha1.update(buffer, 0, read);
                if (size < PREFIX_SIZE) prefix.update(buffer, 0, (int) Math.min(read, PREFIX_SIZE - size));
                size += read;
                checkSize(size);
            }
        }
        return new Digests(size, HexFormat.of().formatHex(md5.digest()), HexFormat.of().formatHex(sha1.digest()),
                HexFormat.of().formatHex(prefix.digest()));
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void checkSize(long size) {
        if (size <= 0 || size > MAX_FILE_SIZE)
            throw new IllegalArgumentException("文件大小必须在 1 字节至 200 MiB 之间");
    }

    private static InputStream base64Input(String data) {
        return new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position == data.length()) return -1;
                char value = data.charAt(position++);
                if (value > 127) throw new IOException("Base64 数据包含非 ASCII 字符");
                return value;
            }
        };
    }

    record Part(int index, long offset, long size, URI uri) {
    }

    record Digests(long size, String md5, String sha1, String prefixMd5) {
    }

    @FunctionalInterface
    interface JsonRequest {
        JsonNode post(String url, Map<String, Object> body) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface PartRequest {
        int put(Path file, Part part, Duration timeout) throws IOException, InterruptedException;
    }

    private static final class NonRetryableUploadException extends IOException {
        private NonRetryableUploadException(int status) {
            super("分片上传失败，HTTP " + status);
        }
    }
}
