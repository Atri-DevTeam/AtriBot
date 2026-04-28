package top.yzljc.qqbot.service.userinfo;

import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetProjectInfo {
    private static final Logger logger = LoggerFactory.getLogger(GetProjectInfo.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = GetProjectInfo.class.getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                logger.warn("Could not find git.properties in classpath. Build info will be empty.");
            }
        } catch (Exception e) {
            logger.error("Failed to load build info", e);
        }
    }

    public static String getVersion() {
        return props.getProperty("git.build.version", "Unknown Version");
    }

    public static String getBuildTime() {
        return props.getProperty("git.build.time", "Unknown Time");
    }

    public static String getCommitId() {
        return props.getProperty("git.commit.id.abbrev", "Unknown Commit");
    }

    public static String getBranch() {
        return props.getProperty("git.branch", "Unknown Branch");
    }
}