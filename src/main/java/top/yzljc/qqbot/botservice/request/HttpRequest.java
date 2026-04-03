package top.yzljc.qqbot.botservice.request;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.utils.Logger;

public class HttpRequest {

    private static final RestTemplate restTemplate = new RestTemplate();

    public static JsonNode sendGetRequest(String url) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                Logger.warn("GET Request failed, HTTP code: {}", response.getStatusCode().value());
            }
        } catch (Exception e) {
            Logger.warn("GET Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }

    public static JsonNode sendPostRequest(String url, Object body) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                Logger.warn("POST Request failed, HTTP code: {}", response.getStatusCode().value());
            }
        } catch (Exception e) {
            Logger.warn("POST Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }
}