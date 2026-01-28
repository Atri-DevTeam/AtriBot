package top.yzljc.qqbot.botkits.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class HttpRequest {
    private static final Logger log = LoggerFactory.getLogger(HttpRequest.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static JsonNode sendGetRequest(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("GET Request failed, HTTP code: {}", code);
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        String errStr = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("Error body: {}", errStr);
                    }
                }
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                String respStr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return jsonMapper.readTree(respStr);
            }
        } catch (Exception e) {
            log.warn("GET Request Error! URL: {}, Error: {}", url, e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}