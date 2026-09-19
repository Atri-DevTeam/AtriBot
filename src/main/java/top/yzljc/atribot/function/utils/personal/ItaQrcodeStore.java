package top.yzljc.atribot.function.utils.personal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 固定入口的两个目标地址和当前状态；成功落盘后才更新内存。 */
final class ItaQrcodeStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path file;
    private State state;

    ItaQrcodeStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    synchronized State snapshot() throws IOException {
        if (state == null) {
            try {
                State loaded = JSON.readValue(Files.readString(file), State.class);
                if (loaded == null) throw new IllegalArgumentException();
                requireSlot(loaded.active());
                if (loaded.groupUrl() == null || loaded.surveyUrl() == null) throw new IllegalArgumentException();
                if (!loaded.groupUrl().isEmpty()) validateUrl(loaded.groupUrl());
                if (!loaded.surveyUrl().isEmpty()) validateUrl(loaded.surveyUrl());
                state = loaded;
            } catch (NoSuchFileException missing) {
                state = new State(1, "", "");
            } catch (IllegalArgumentException invalid) {
                throw new IOException("ITA 二维码配置无效", invalid);
            }
        }
        return state;
    }

    synchronized State setUrl(int slot, String url) throws IOException {
        requireSlot(slot);
        String target = validateUrl(url);
        State before = snapshot();
        return save(new State(before.active(), slot == 1 ? target : before.groupUrl(),
                slot == 2 ? target : before.surveyUrl()));
    }

    synchronized State activate(int slot) throws IOException {
        requireSlot(slot);
        State before = snapshot();
        if (before.url(slot).isEmpty()) {
            throw new IllegalArgumentException("请先设置" + name(slot) + "的地址，再切换状态。");
        }
        if (before.active() == slot) return before;
        return save(new State(slot, before.groupUrl(), before.surveyUrl()));
    }

    private State save(State next) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ita-qrcode-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), next);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            state = next;
            return next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String validateUrl(String value) {
        try {
            String url = value == null ? "" : value.strip();
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() < -1 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException invalid) {
            // 不回显用户输入，避免配置地址出现在消息中。
            throw new IllegalArgumentException("请输入有效的 HTTP 或 HTTPS 地址（不能包含账号密码）。");
        }
    }

    static String name(int slot) {
        requireSlot(slot);
        return slot == 1 ? "群状态" : "问卷状态";
    }

    private static void requireSlot(int slot) {
        if (slot != 1 && slot != 2) throw new IllegalArgumentException("状态编号只能是 1 或 2。");
    }

    record State(int active, String groupUrl, String surveyUrl) {
        String url(int slot) {
            requireSlot(slot);
            return slot == 1 ? groupUrl : surveyUrl;
        }

        String target() {
            return url(active);
        }

        String statusText() {
            return "ITA 二维码\n当前状态：" + name(active)
                    + (target().isEmpty() ? "（未配置，入口暂不可用）" : "")
                    + "\n群状态：" + (groupUrl.isEmpty() ? "未配置" : "已配置")
                    + "\n问卷状态：" + (surveyUrl.isEmpty() ? "未配置" : "已配置");
        }
    }
}
