package top.yzljc.atribot.utils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName AtomicFiles
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    /**
     * 在目标目录写入临时文件后替换目标文件，仅在文件系统不支持原子移动时使用普通替换
     * 同一文件的并发更新由调用方协调；此方法不保证断电后的数据持久性
     *
     * @param file 目标文件，支持相对路径，父目录不存在时自动创建
     * @param content 完整文件内容
     * @throws IOException 创建目录、写入、替换或清理临时文件失败
     * @throws NullPointerException file 或 content 为 null
     */
    public static void write(Path file, byte[] content) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(content, "content");
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Target must have a parent directory: " + target);
        }
        Files.createDirectories(parent);
        try (TemporaryFile temporary = new TemporaryFile(Files.createTempFile(parent, ".atri-", ".tmp"))) {
            Files.write(temporary.path(), content);
            try {
                Files.move(temporary.path(), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.path(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private record TemporaryFile(Path path) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
