package top.yzljc.atribot.config;

import lombok.Data;

@Data
public class AiProperties {

    private String apiKey;

    private String baseUrl;

    private String model;

    private int timeout = 30000;
}