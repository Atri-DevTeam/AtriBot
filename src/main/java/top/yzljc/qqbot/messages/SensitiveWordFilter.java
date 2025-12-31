package top.yzljc.qqbot.messages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 敏感词过滤工具类
 * 读取根目录下的 filter.yml 进行过滤
 */
public class SensitiveWordFilter {

    private static final String CONFIG_FILE = "filter.yml";
    private static final List<String> BLACKLIST = new CopyOnWriteArrayList<>();
    private static long lastModifiedTime = 0;

    static {
        // 类加载时初始化加载一次
        reload();
    }

    /**
     * 重新加载配置文件
     */
    public static void reload() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            System.err.println("[INFO] 未找到 " + CONFIG_FILE + "，跳过过滤加载。");
            return;
        }

        // 如果文件没变动，则不重新加载 (可选，这里为了简单每次调用reload都重读)
        if (file.lastModified() == lastModifiedTime) {
            return;
        }

        List<String> temp = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 简单的 YAML 解析：查找以 "- " 开头的行
                if (line.startsWith("- ")) {
                    String word = line.substring(2).trim();
                    if (!word.isEmpty()) {
                        temp.add(word);
                    }
                }
            }

            BLACKLIST.clear();
            BLACKLIST.addAll(temp);
            lastModifiedTime = file.lastModified();
            System.out.println("[INFO] 已加载 " + BLACKLIST.size() + " 个违规词。");

        } catch (Exception e) {
            System.err.println("[INFO] 读取配置文件失败: " + e.getMessage());
        }
    }

    public static String findSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        checkReload(); // 检查热更新

        for (String word : BLACKLIST) {
            if (text.contains(word)) {
                return word; // 返回具体命中的词
            }
        }
        return null;
    }

    /**
     * 检查文本是否包含任意违规词
     * @param text 待检查文本
     * @return true=包含违规词, false=安全
     */
    public static boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // 每次检查前尝试刷新（如果文件被修改）
        // 为了性能，建议不要每条消息都查文件属性，这里做一个简单的策略：
        // 如果你需要实时热更新，保留下面这行；如果不需要，注释掉下面这行即可。
        checkReload();

        for (String word : BLACKLIST) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // 简单的限流重载检查，防止频繁IO
    private static long lastCheckTime = 0;
    private static void checkReload() {
        long now = System.currentTimeMillis();
        // 每 60 秒检查一次文件变动
        if (now - lastCheckTime > 60000) {
            reload();
            lastCheckTime = now;
        }
    }
}