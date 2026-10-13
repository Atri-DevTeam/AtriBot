package top.yzljc.atribot.function.background;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.utils.HashUtils;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundClient
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.background
 */
public final class BackgroundClient {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private BackgroundClient() {}

    public static JsonNode upload(String userId, String id, String attachmentUrl) throws Exception {
        URI source = attachmentUri(attachmentUrl);
        var request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(30)).GET().build();
        var response = HTTP.send(request, limitedBody(MAX_BYTES));
        if (response.statusCode() != 200) throw new IOException("附件读取失败，请重新发送图片");
        byte[] bytes = response.body();
        if (bytes.length == 0) throw new IllegalArgumentException("图片内容为空");
        return json(request("bot.background.upload", userId, id).header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
    }

    public static byte[] image(String userId, String id, boolean thumbnail) throws Exception {
        String url = endpoint("bot.background.image", userId, id) + "?thumbnail=" + thumbnail;
        var response = HTTP.send(authorized(url).GET().build(), limitedBody(MAX_BYTES));
        if (response.statusCode() != 200) throw new IOException("背景图片读取失败");
        return response.body();
    }

    public static void delete(String userId, String id) throws Exception {
        json(request("bot.background.delete", userId, id).DELETE().build());
    }

    public static void retain(String userId, Set<String> ids) throws Exception {
        json(request("bot.background.retain", userId, "").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(ids))).build());
    }

    static URI attachmentUri(String url) {
        URI uri = URI.create(url.startsWith("https://") ? url : "https://" + url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !(host.equals("multimedia.nt.qq.com.cn") || host.equals("gchat.qpic.cn")
                || host.equals("c2cpicdw.qpic.cn") || host.endsWith(".qpic.cn")))
            throw new IllegalArgumentException("请通过 QQ 消息附件提交背景图片");
        return uri;
    }

    private static String endpoint(String key, String userId, String id) {
        String owner = HashUtils.sha256Hex(userId.getBytes(StandardCharsets.UTF_8));
        return OpenApi.get(key).replace("{owner}", owner).replace("{id}", id);
    }

    private static HttpRequest.Builder request(String key, String userId, String id) {
        return authorized(endpoint(key, userId, id));
    }

    private static HttpRequest.Builder authorized(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret());
    }

    private static JsonNode json(HttpRequest request) throws Exception {
        var response = HTTP.send(request, limitedBody(65536));
        if (response.statusCode() == 429) throw new IllegalArgumentException("背景服务繁忙，请稍后重试");
        JsonNode data = JSON.readTree(response.body());
        if (response.statusCode() == 400 && data != null && data.path("code").asInt() == 19999)
            throw new IllegalArgumentException(data.path("message").asText("背景图片无效"));
        if (response.statusCode() < 200 || response.statusCode() >= 300 || data == null || data.path("code").asInt(-1) != 0)
            throw new IOException("背景服务处理失败（HTTP " + response.statusCode() + "）");
        return data.path("data");
    }

    /**
     * 在接收过程中限制响应体，超限立即取消订阅；请求超时覆盖整个响应体接收阶段。
     *
     * @param limit 允许接收的最大字节数
     * @return 有大小上限的响应体处理器
     */
    static HttpResponse.BodyHandler<byte[]> limitedBody(int limit) {
        return info -> new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            private Flow.Subscription subscription;
            private long received;
            private boolean done;

            @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            @Override public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                delegate.onSubscribe(value);
            }
            @Override public void onNext(List<ByteBuffer> buffers) {
                if (done) return;
                for (ByteBuffer buffer : buffers) received += buffer.remaining();
                if (received > limit) {
                    done = true;
                    subscription.cancel();
                    delegate.onError(new IOException("图片响应超过允许大小"));
                } else delegate.onNext(buffers);
            }
            @Override public void onError(Throwable error) {
                if (!done) { done = true; delegate.onError(error); }
            }
            @Override public void onComplete() {
                if (!done) { done = true; delegate.onComplete(); }
            }
        };
    }
}
