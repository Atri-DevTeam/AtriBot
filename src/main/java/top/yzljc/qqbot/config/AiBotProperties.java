package top.yzljc.qqbot.config;

import lombok.Data;

@Data
public class AiBotProperties {
    private String apiKey;
    private String baseUrl;
    private String model;
    private int timeout = 30000;
}