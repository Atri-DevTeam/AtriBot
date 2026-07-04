package top.yzljc.atribot.service.ai;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiProperties {

    private String apiKey;

    private String baseUrl;

    private String model;

    private int timeout = 30000000;

    private Map<String, Object> extraBody = new LinkedHashMap<>();
}