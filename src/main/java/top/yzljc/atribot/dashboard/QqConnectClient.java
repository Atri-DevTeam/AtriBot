package top.yzljc.atribot.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * QQ 互联网站应用授权客户端
 *
 * @Author YZ_Ljc_
 * @ClassName QqConnectClient
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.dashboard
 */
public final class QqConnectClient {
    private static final Logger LOG = LoggerFactory.getLogger(QqConnectClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public static String authorizeUrl(DashboardSettings settings, String state, boolean mobile) {
        return "https://graph.qq.com/oauth2.0/authorize?response_type=code&client_id=" + encode(settings.appId())
                + "&redirect_uri=" + encode(settings.callbackUrl()) + "&state=" + encode(state)
                + "&scope=get_user_info" + (mobile ? "&display=mobile" : "");
    }

    /**
     * 使用授权码在服务端确认 QQ 互联身份，不保留平台令牌
     *
     * @param settings 网站应用配置
     * @param code     QQ 回调提供的授权码
     * @return 当前网站应用范围内的用户 OpenID 和 QQ 头像地址
     * @throws IOException          平台请求失败或身份响应无效
     * @throws InterruptedException 请求被中断
     */
    public Profile profile(DashboardSettings settings, String code) throws IOException, InterruptedException {
        JsonNode token = request("https://graph.qq.com/oauth2.0/token?grant_type=authorization_code&fmt=json&client_id="
                + encode(settings.appId()) + "&client_secret=" + encode(settings.appKey()) + "&code=" + encode(code)
                + "&redirect_uri=" + encode(settings.callbackUrl()));
        String accessToken = token.path("access_token").asText("");
        if (token.has("error") || accessToken.isBlank()) throw new IOException("QQ token response invalid");
        JsonNode identity = request("https://graph.qq.com/oauth2.0/me?fmt=json&access_token=" + encode(accessToken));
        String openId = identity.path("openid").asText("");
        if (identity.has("error") || !settings.appId().equals(identity.path("client_id").asText())
                || !openId.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("QQ identity response invalid");
        String avatarUrl = null;
        try {
            JsonNode profile = request("https://graph.qq.com/user/get_user_info?access_token=" + encode(accessToken)
                    + "&oauth_consumer_key=" + encode(settings.appId()) + "&openid=" + encode(openId));
            if (!profile.path("ret").isIntegralNumber() || profile.path("ret").asInt() != 0)
                throw new IOException("QQ profile response invalid");
            String avatar = profile.path("figureurl_qq_1").asText("").replaceFirst("/40(?=[?#]|$)", "/0");
            if (!avatar.isBlank()) {
                URI uri = URI.create(avatar);
                if (uri.getHost() != null && uri.getUserInfo() == null
                        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                    avatarUrl = "http".equalsIgnoreCase(uri.getScheme()) ? "https" + avatar.substring(4) : avatar;
            }
        } catch (IOException | IllegalArgumentException failure) {
            LOG.warn("QQ 互联头像获取失败: errorType={}", failure.getClass().getSimpleName());
        }
        return new Profile(openId, avatarUrl);
    }

    public record Profile(String openId, String avatarUrl) {
    }

    private JsonNode request(String url) throws IOException, InterruptedException {
        // 请求地址包含应用密钥或令牌，不使用会记录完整 URL 的通用 HTTP 日志
        var response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().length() > 65536)
            throw new IOException("QQ OAuth request failed");
        String body = response.body().trim();
        if (body.startsWith("callback")) {
            int first = body.indexOf('(');
            int last = body.lastIndexOf(')');
            if (first < 0 || last <= first) throw new IOException("QQ OAuth response invalid");
            body = body.substring(first + 1, last).trim();
        }
        try {
            JsonNode result = JSON.readTree(body);
            if (result == null || !result.isObject()) throw new IOException("QQ OAuth response invalid");
            return result;
        } catch (IOException invalid) {
            throw new IOException("QQ OAuth response invalid");
        }
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
