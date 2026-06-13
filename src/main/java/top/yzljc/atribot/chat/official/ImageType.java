package top.yzljc.atribot.chat.official;

import lombok.Getter;

@Getter
public enum ImageType {
    URL("url"),
    BASE64("file_data");

    private final String dataKey;

    ImageType(String dataKey) {
        this.dataKey = dataKey;
    }
}