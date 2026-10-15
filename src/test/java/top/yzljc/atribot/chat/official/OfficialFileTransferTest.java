package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.yzljc.atribot.platform.qq.FileType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialFileTransferTest
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
class OfficialFileTransferTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GROUP_URL = "https://api.example.test/v2/groups/group-id/files";
    private static final String USER_URL = "https://api.example.test/v2/users/user-id/files";
    @TempDir Path directory;

    @Test
    void directUploadIncludesFilenameAndDoesNotSendMessage() throws Exception {
        byte[] bytes = data(OfficialFileTransfer.DIRECT_LIMIT);
        Path file = Files.write(directory.resolve("example.mp4"), bytes);
        for (String url : List.of(GROUP_URL, USER_URL)) {
            AtomicInteger requests = new AtomicInteger();
            OfficialFileTransfer transfer = new OfficialFileTransfer((endpoint, body) -> {
                requests.incrementAndGet();
                assertEquals(url, endpoint);
                assertEquals(2, body.get("file_type"));
                assertEquals("example.mp4", body.get("file_name"));
                assertEquals(false, body.get("srv_send_msg"));
                assertArrayEquals(bytes, Base64.getDecoder().decode((String) body.get("file_data")));
                assertFalse(body.containsKey("url"));
                return success();
            }, (path, part, timeout) -> fail("小文件不应分片"));
            assertEquals("file-token", transfer.upload(url, FileType.VIDEO, file, "example.mp4").path("file_info").asText());
            assertEquals(1, requests.get());
        }
    }

    @Test
    void base64PrefixesAreDecodedBeforeUpload() throws Exception {
        byte[] bytes = data(128);
        for (String prefix : List.of("", "base64://", "data:image/png;base64,")) {
            var transfer = new OfficialFileTransfer((url, body) -> {
                assertArrayEquals(bytes, Base64.getDecoder().decode((String) body.get("file_data")));
                assertEquals("image.png", body.get("file_name"));
                return success();
            }, (path, part, timeout) -> fail("小文件不应分片"));
            transfer.uploadBase64(GROUP_URL, FileType.IMAGE, prefix + Base64.getEncoder().encodeToString(bytes), "image.png");
        }
    }

    @Test
    void multipartUploadsExactRangesAndConfirmsEveryPartBeforeMerge() throws Exception {
        byte[] bytes = data(11_000_123);
        Path file = Files.write(directory.resolve("large.bin"), bytes);
        AtomicInteger uploads = new AtomicInteger();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        int blockSize = 4_000_000;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/part", exchange -> {
            try {
                int index = Integer.parseInt(exchange.getRequestURI().getQuery());
                byte[] expected = Arrays.copyOfRange(bytes, index * blockSize, Math.min(bytes.length, (index + 1) * blockSize));
                assertEquals("PUT", exchange.getRequestMethod());
                assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                assertEquals(Integer.toString(expected.length), exchange.getRequestHeaders().getFirst("Content-Length"));
                assertArrayEquals(expected, exchange.getRequestBody().readAllBytes());
                uploads.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
            } catch (Throwable e) {
                serverFailure.set(e);
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String url : List.of(GROUP_URL, USER_URL)) {
                List<String> order = new ArrayList<>();
                var transfer = new OfficialFileTransfer((endpoint, body) -> {
                    assertTrue(endpoint.startsWith(url.substring(0, url.length() - 6)));
                    if (endpoint.endsWith("/upload_prepare")) {
                        order.add("prepare");
                        assertEquals(Integer.toString(bytes.length), body.get("file_size"));
                        assertEquals(hash("MD5", bytes), body.get("md5"));
                        assertEquals(hash("SHA-1", bytes), body.get("sha1"));
                        assertEquals(hash("MD5", Arrays.copyOf(bytes, 10_002_432)), body.get("md5_10m"));
                        return prepared(bytes.length, blockSize, server.getAddress().getPort());
                    }
                    if (endpoint.endsWith("/upload_part_finish")) {
                        int index = (Integer) body.get("part_index");
                        assertEquals("put" + index, order.getLast());
                        byte[] part = Arrays.copyOfRange(bytes, index * blockSize, Math.min(bytes.length, (index + 1) * blockSize));
                        assertEquals(Integer.toString(part.length), body.get("block_size"));
                        assertEquals(hash("MD5", part), body.get("md5"));
                        order.add("finish" + index);
                        return MAPPER.createObjectNode();
                    }
                    assertEquals(url, endpoint);
                    assertEquals(List.of("prepare", "put0", "finish0", "put1", "finish1", "put2", "finish2"), order);
                    assertEquals("upload-1", body.get("upload_id"));
                    assertEquals("large.bin", body.get("file_name"));
                    assertEquals(false, body.get("srv_send_msg"));
                    assertFalse(body.containsKey("file_data"));
                    order.add("merge");
                    return success();
                }, (path, part, timeout) -> {
                    order.add("put" + part.index());
                    var request = HttpRequest.newBuilder(part.uri()).timeout(timeout)
                            .PUT(OfficialFileTransfer.partBody(path, part)).build();
                    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                });
                assertEquals("file-token", transfer.upload(url, FileType.FILE, file, "large.bin").path("file_info").asText());
                assertEquals("merge", order.getLast());
            }
            assertEquals(6, uploads.get());
            assertNull(serverFailure.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transientPartFailureRetriesWithoutPrematureConfirmation() throws Exception {
        Path file = Files.write(directory.resolve("retry.bin"), data(OfficialFileTransfer.DIRECT_LIMIT + 1));
        AtomicInteger puts = new AtomicInteger();
        List<String> calls = new ArrayList<>();
        var transfer = new OfficialFileTransfer((url, body) -> {
            calls.add(url);
            if (url.endsWith("/upload_prepare")) {
                var result = prepared(OfficialFileTransfer.DIRECT_LIMIT + 1, 6_000_000, 1234);
                ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("upload_config")).put("retry_timeout", 3);
                return result;
            }
            assertEquals(2, puts.get());
            return url.endsWith("/files") ? success() : MAPPER.createObjectNode();
        }, (path, part, timeout) -> puts.incrementAndGet() == 1 ? 503 : 200);
        transfer.upload(GROUP_URL, FileType.FILE, file, "retry.bin");
        assertEquals(3, calls.size());
    }

    @Test
    void permanentPartFailureDoesNotRetryConfirmOrMerge() throws Exception {
        Path file = Files.write(directory.resolve("failure.bin"), data(OfficialFileTransfer.DIRECT_LIMIT + 1));
        AtomicInteger puts = new AtomicInteger();
        AtomicInteger posts = new AtomicInteger();
        var transfer = new OfficialFileTransfer((url, body) -> {
            assertEquals(1, posts.incrementAndGet());
            return prepared(OfficialFileTransfer.DIRECT_LIMIT + 1, 6_000_000, 1234);
        }, (path, part, timeout) -> { puts.incrementAndGet(); return 403; });
        assertThrows(IOException.class, () -> transfer.upload(USER_URL, FileType.FILE, file, "failure.bin"));
        assertEquals(1, puts.get());
    }

    @Test
    void confirmationFailurePreventsMerge() throws Exception {
        Path file = Files.write(directory.resolve("failure.bin"), data(OfficialFileTransfer.DIRECT_LIMIT + 1));
        List<String> calls = new ArrayList<>();
        var transfer = new OfficialFileTransfer((url, body) -> {
            calls.add(url);
            if (url.endsWith("/upload_prepare")) return prepared(OfficialFileTransfer.DIRECT_LIMIT + 1, 6_000_000, 1234);
            assertTrue(url.endsWith("/upload_part_finish"));
            throw new IOException("确认失败");
        }, (path, part, timeout) -> 200);
        assertThrows(IOException.class, () -> transfer.upload(GROUP_URL, FileType.FILE, file, "failure.bin"));
        assertEquals(2, calls.size());
    }

    @Test
    void interruptedBase64UploadDeletesTemporaryFile() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(data(OfficialFileTransfer.DIRECT_LIMIT + 1));
        AtomicReference<Path> temporary = new AtomicReference<>();
        var transfer = new OfficialFileTransfer((url, body) -> prepared(OfficialFileTransfer.DIRECT_LIMIT + 1, 6_000_000, 1234),
                (path, part, timeout) -> { temporary.set(path); throw new InterruptedException(); });
        assertThrows(InterruptedException.class, () -> transfer.uploadBase64(USER_URL, FileType.IMAGE, base64, "image.png"));
        assertNotNull(temporary.get());
        assertFalse(Files.exists(temporary.get()));
    }

    @Test
    void invalidPartPlanIsRejectedBeforeUpload() throws Exception {
        var plan = prepared(8_000_001, 4_000_000, 1234);
        ((com.fasterxml.jackson.databind.node.ObjectNode) plan.path("parts").get(1)).put("index", 0);
        assertThrows(IOException.class, () -> OfficialFileTransfer.parseParts(plan, 8_000_001));
        var missing = prepared(8_000_001, 4_000_000, 1234);
        ((com.fasterxml.jackson.databind.node.ArrayNode) missing.path("parts")).remove(2);
        assertThrows(IOException.class, () -> OfficialFileTransfer.parseParts(missing, 8_000_001));
    }

    @Test
    void emptyAndOversizeFilesAreRejectedBeforeNetworkRequests() throws Exception {
        Path file = directory.resolve("invalid.bin");
        var transfer = new OfficialFileTransfer((url, body) -> fail("无效文件不应请求平台"),
                (path, part, timeout) -> fail("无效文件不应上传分片"));
        Files.createFile(file);
        assertThrows(IllegalArgumentException.class, () -> transfer.upload(GROUP_URL, FileType.FILE, file, "invalid.bin"));
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(OfficialFileTransfer.MAX_FILE_SIZE + 1);
        }
        assertThrows(IllegalArgumentException.class, () -> transfer.upload(GROUP_URL, FileType.FILE, file, "invalid.bin"));
    }

    private static JsonNode prepared(int size, int block, int port) {
        var result = MAPPER.createObjectNode();
        result.put("upload_id", "upload-1");
        result.put("block_size", Integer.toString(block));
        var parts = result.putArray("parts");
        for (int i = 0; i < (size - 1) / block + 1; i++) {
            parts.addObject().put("index", i).put("block_size", Integer.toString(Math.min(block, size - i * block)))
                    .put("presigned_url", "http://127.0.0.1:" + port + "/part?" + i);
        }
        result.putObject("upload_config").put("retry_timeout", 0).put("retry_delay", 1).put("concurrency", 1);
        return result;
    }

    private static JsonNode success() {
        return MAPPER.valueToTree(Map.of("file_info", "file-token", "ttl", 300));
    }

    private static byte[] data(int length) {
        byte[] bytes = new byte[length];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    private static String hash(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
