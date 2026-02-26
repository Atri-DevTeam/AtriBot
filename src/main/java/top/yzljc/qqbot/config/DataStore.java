package top.yzljc.qqbot.config;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataStore {
    private static final Logger log = LoggerFactory.getLogger(DataStore.class);
    private static final String DATA_FILE = "data.yml";

    public static void saveLikeUserUids(List<Long> uidList) {
        try {
            Path dataPath = Paths.get(DATA_FILE);
            Map<String, Object> data = new HashMap<>();
            data.put("like-user-uids", new ArrayList<>(uidList));

            Yaml yaml = new Yaml();
            String ymlOut = yaml.dump(data);
            Files.writeString(dataPath, ymlOut, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("保存 like-user-uids 到 data.yml 失败", e);
        }
    }

    public static List<Long> loadLikeUserUids() {
        List<Long> uidList = new ArrayList<>();
        try {
            Path dataPath = Paths.get(DATA_FILE);
            if (!Files.exists(dataPath)) {
                return uidList;
            }
            try (InputStream is = Files.newInputStream(dataPath)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null && data.get("like-user-uids") instanceof List<?> list) {
                    for (Object uid : list) {
                        try {
                            uidList.add(((Number) uid).longValue());
                        } catch (Exception e) {
                            log.warn("data.yml 中 like-user-uids 含有无效 uid: {}", uid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 like-user-uids 出错", e);
        }
        return uidList;
    }
}