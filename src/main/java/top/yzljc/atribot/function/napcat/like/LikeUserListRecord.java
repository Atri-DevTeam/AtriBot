package top.yzljc.atribot.function.napcat.like;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LikeUserListRecord {
    private static final Logger log = LoggerFactory.getLogger(LikeUserListRecord.class);
    private static final String DATA_FILE = "data.yml";

    public static Map<String, Object> loadFullData() {
        try {
            Path dataPath = Paths.get(DATA_FILE);
            if (!Files.exists(dataPath)) {
                return new HashMap<>();
            }
            try (InputStream is = Files.newInputStream(dataPath)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                return data != null ? new HashMap<>(data) : new HashMap<>();
            }
        } catch (Exception e) {
            log.warn("读取 data.yml 出错", e);
            return new HashMap<>();
        }
    }

    public static void saveFullData(Map<String, Object> data) {
        try {
            Path dataPath = Paths.get(DATA_FILE);
            Yaml yaml = new Yaml();
            String ymlOut = yaml.dump(data);
            Files.writeString(dataPath, ymlOut, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("保存 data.yml 失败", e);
        }
    }

    public static void saveLikeUserUids(List<Long> uidList) {
        Map<String, Object> data = loadFullData();
        data.put("like-user-uids", new ArrayList<>(uidList));
        saveFullData(data);
    }

    public static List<Long> loadLikeUserUids() {
        List<Long> uidList = new ArrayList<>();
        Map<String, Object> data = loadFullData();
        if (data.get("like-user-uids") instanceof List<?> list) {
            for (Object uid : list) {
                try {
                    uidList.add(((Number) uid).longValue());
                } catch (Exception e) {
                    log.warn("data.yml 中 like-user-uids 含有无效 uid: {}", uid);
                }
            }
        }
        return uidList;
    }

    public static String loadBwcApiKey() {
        Object v = loadFullData().get("bwc-api-key");
        return v != null ? v.toString().trim() : "";
    }

    public static void saveBwcApiKey(String key) {
        if (key == null) key = "";
        Map<String, Object> data = loadFullData();
        data.put("bwc-api-key", key.trim());
        saveFullData(data);
    }
}