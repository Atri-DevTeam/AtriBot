package top.yzljc.qqbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "atri.ai")
public class AiBotProperties {
    private String apiKey;
    private String baseUrl;
    private String model;
    private int timeout = 30000;

}