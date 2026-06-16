package top.yzljc.atribot.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName YamlConfiguration
 * @Created_at 2026/05/28
 * @Project AtriBot
 * @Package top.yzljc.atribot.utils
 */
public class YamlConfiguration {

    private final File file;
    private final Yaml yaml;
    
    private Map<String, Object> map = new LinkedHashMap<>();
    private final Map<String, Object> defaults = new LinkedHashMap<>();

    public YamlConfiguration(File file) {
        this.file = file;

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        this.yaml = new Yaml(options);
    }

    @SuppressWarnings("unchecked")
    public void load() {
        try {
            if (!file.exists()) {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
                this.map = new LinkedHashMap<>();
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Object loaded = yaml.load(reader);
                if (loaded instanceof Map) {
                    this.map = (Map<String, Object>) loaded;
                } else {
                    this.map = new LinkedHashMap<>();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件失败: " + file.getName(), e);
        }
    }

    public void save() {
        try {
            if (!file.exists()) {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                yaml.dump(map, writer);
            }
        } catch (Exception e) {
            throw new RuntimeException("保存配置文件失败: " + file.getName(), e);
        }
    }

    public void reload() {
        load();
    }

    public void addDefault(String path, Object value) {
        defaults.put(path, value);
    }

    public void copyDefaults() {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!contains(entry.getKey())) {
                set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        // 如果有变化，可以考虑在这里触发自动保存，或者由外部调用 config.save()
    }

    public boolean contains(String path) {
        return get(path) != null;
    }

    public Object get(String path) {
        if (path == null || path.isEmpty()) return map;
        
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public Object get(String path, Object def) {
        Object value = get(path);
        return value == null ? def : value;
    }

    @SuppressWarnings("unchecked")
    public void set(String path, Object value) {
        if (path == null || path.isEmpty()) return;

        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            
            // 如果下一层不是 Map，则强制覆盖为一个新的 Map
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(part, next);
            }
            current = (Map<String, Object>) next;
        }

        // 如果 value 是 null，代表删除该节点
        if (value == null) {
            current.remove(parts[parts.length - 1]);
        } else {
            current.put(parts[parts.length - 1], value);
        }
    }

    public String getString(String path) {
        Object value = get(path);
        return value == null ? null : String.valueOf(value);
    }

    public String getString(String path, String def) {
        String value = getString(path);
        return value == null ? def : value;
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }

    public int getInt(String path, int def) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return def;
    }

    public long getLong(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    public double getDouble(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0D;
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object value = get(path);
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object obj : (List<?>) value) {
                result.add(String.valueOf(obj));
            }
        }
        return result;
    }

    public List<Integer> getIntegerList(String path) {
        Object value = get(path);
        List<Integer> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object obj : (List<?>) value) {
                if (obj instanceof Number) {
                    result.add(((Number) obj).intValue());
                }
            }
        }
        return result;
    }

    /*
     * =========================
     * Section 和 Keys
     * =========================
     */

    @SuppressWarnings("unchecked")
    public ConfigSection getSection(String path) {
        Object value = get(path);
        if (value instanceof Map) {
            return new ConfigSection((Map<String, Object>) value);
        }
        return null;
    }

    public Set<String> getKeys() {
        return map.keySet();
    }

    public Map<String, Object> getRawMap() {
        return map;
    }

    public static class ConfigSection {
        private final Map<String, Object> section;

        public ConfigSection(Map<String, Object> section) {
            this.section = section;
        }

        public Object get(String key) {
            return section.get(key);
        }

        public String getString(String key) {
            Object value = section.get(key);
            return value == null ? null : String.valueOf(value);
        }

        public int getInt(String key) {
            Object value = section.get(key);
            if (value instanceof Number) return ((Number) value).intValue();
            return 0;
        }

        public boolean getBoolean(String key) {
            Object value = section.get(key);
            if (value instanceof Boolean) return (Boolean) value;
            return false;
        }

        public List<String> getStringList(String key) {
            Object value = section.get(key);
            List<String> result = new ArrayList<>();
            if (value instanceof List) {
                for (Object obj : (List<?>) value) {
                    result.add(String.valueOf(obj));
                }
            }
            return result;
        }

        public Set<String> getKeys() {
            return section.keySet();
        }

        public Map<String, Object> getRawMap() {
            return section;
        }
    }
}