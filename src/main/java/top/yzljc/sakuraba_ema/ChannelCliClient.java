package top.yzljc.sakuraba_ema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliException;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.sakuraba_ema.guild.impl.ChannelFeedClient;
import top.yzljc.sakuraba_ema.manager.ChannelManageClient;
import top.yzljc.sakuraba_ema.manager.ChannelSystemClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
public final class ChannelCliClient implements AutoCloseable {

    private static final Pattern COMMAND_PART = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Duration RATE_LIMIT_RETRY_DELAY = Duration.ofSeconds(70);
    private static final Duration PROCESS_STOP_GRACE = Duration.ofSeconds(2);

    @Getter
    private final boolean enabled;
    private final String cliPath;
    private final String loginToken;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final Semaphore invocationPermit = new Semaphore(1, true);
    private final Set<ProcessHandle> activeProcesses = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ChannelFeedClient feed;
    private final ChannelManageClient manage;
    private final ChannelSystemClient system;

    public ChannelCliClient(boolean enabled, String cliPath, String loginToken, Duration timeout) {
        this(enabled, cliPath, loginToken, timeout, new ObjectMapper());
    }

    public ChannelCliClient(boolean enabled, String cliPath, String loginToken,
                            Duration timeout, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.cliPath = cliPath == null || cliPath.isBlank() ? "tencent-channel-cli" : cliPath.trim();
        this.loginToken = loginToken == null ? "" : loginToken.trim();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.feed = new ChannelFeedClient(this);
        this.manage = new ChannelManageClient(this);
        this.system = new ChannelSystemClient(this);
    }

    public ChannelFeedClient feed() {
        return feed;
    }

    public ChannelManageClient manage() {
        return manage;
    }

    public ChannelSystemClient system() {
        return system;
    }

    public ChannelCliResult execute(String domain, String action, JsonNode parameters) {
        return execute(domain, action, parameters, ChannelCliOptions.DEFAULT);
    }

    public ChannelCliResult execute(String domain, String action, JsonNode parameters,
                                    ChannelCliOptions options) {
        validateCommandPart(domain, "domain");
        validateCommandPart(action, "action");
        return executeCommand(List.of(domain, action), parameters, options, true);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String domain, String action,
                                                            JsonNode parameters) {
        return executeAsync(domain, action, parameters, ChannelCliOptions.DEFAULT);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String domain, String action,
                                                            JsonNode parameters,
                                                            ChannelCliOptions options) {
        return ThreadManager.supplyAsync(() -> execute(domain, action, parameters, options));
    }

    public ChannelCliResult executeCommand(List<String> command, JsonNode parameters,
                                           ChannelCliOptions options, boolean jsonOutput) {
        ensureAvailable();
        Objects.requireNonNull(command, "command");
        ChannelCliOptions actualOptions = options == null ? ChannelCliOptions.DEFAULT : options;

        try {
            invocationPermit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChannelCliException("等待 tencent-channel-cli 调用时被中断", e);
        }

        try {
            ensureAvailable();
            ChannelCliResult first = executeOnce(command, parameters, actualOptions, jsonOutput);
            if (!first.isRateLimited()) {
                logFailure(command, first);
                return first;
            }

            log.warn("tencent-channel-cli 触发接口频率限制，将在 {} 秒后重试一次",
                    RATE_LIMIT_RETRY_DELAY.toSeconds());
            try {
                Thread.sleep(RATE_LIMIT_RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChannelCliException("等待 tencent-channel-cli 限流重试时被中断", e);
            }
            ensureAvailable();
            ChannelCliResult retry = executeOnce(command, parameters, actualOptions, jsonOutput).withAttempts(2);
            logFailure(command, retry);
            return retry;
        } finally {
            invocationPermit.release();
        }
    }

    private ChannelCliResult executeOnce(List<String> command, JsonNode parameters,
                                         ChannelCliOptions options, boolean jsonOutput) {
        List<String> arguments = new ArrayList<>(command);
        if (jsonOutput) {
            arguments.add("--json");
        }
        if (options.dryRun()) {
            arguments.add("--dry-run");
        }
        if (options.yes()) {
            arguments.add("--yes");
        }

        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildPlatformCommand(arguments));
            if (!loginToken.isBlank()) {
                processBuilder.environment().put("QQ_AI_CONNECT_TOKEN", loginToken);
            }
            process = processBuilder.start();
        } catch (IOException e) {
            log.error("腾讯频道 CLI 进程启动失败: cliPath={}", cliPath, e);
            throw new ChannelCliException(
                    "无法启动 tencent-channel-cli，请检查 tencent-channel.cli-path 配置", e);
        }

        ProcessHandle handle = process.toHandle();
        activeProcesses.add(handle);
        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");
        AtomicReference<Throwable> stdoutFailure = new AtomicReference<>();
        AtomicReference<Throwable> stderrFailure = new AtomicReference<>();
        Thread stdoutReader = readStream(process.getInputStream(), stdout, stdoutFailure, "stdout");
        Thread stderrReader = readStream(process.getErrorStream(), stderr, stderrFailure, "stderr");

        boolean timedOut = false;
        int exitCode = -1;
        try {
            try (var input = process.getOutputStream()) {
                if (parameters != null && !parameters.isMissingNode() && !parameters.isNull()) {
                    objectMapper.writeValue(input, parameters);
                    input.write('\n');
                }
            }

            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
            } else {
                timedOut = true;
                terminateProcessTree(handle);
                process.waitFor(PROCESS_STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
                if (!process.isAlive()) {
                    exitCode = process.exitValue();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminateProcessTree(handle);
            log.error("腾讯频道 CLI 调用被中断: command={}", commandName(command), e);
            throw new ChannelCliException("tencent-channel-cli 调用被中断", e);
        } catch (IOException e) {
            terminateProcessTree(handle);
            log.error("腾讯频道 CLI 参数写入失败: command={}", commandName(command), e);
            throw new ChannelCliException("写入 tencent-channel-cli 参数失败", e);
        } finally {
            activeProcesses.remove(handle);
            joinReader(stdoutReader);
            joinReader(stderrReader);
        }

        if (stdoutFailure.get() != null || stderrFailure.get() != null) {
            Throwable cause = stdoutFailure.get() != null ? stdoutFailure.get() : stderrFailure.get();
            log.error("腾讯频道 CLI 输出读取失败: command={}", commandName(command), cause);
            throw new ChannelCliException("读取 tencent-channel-cli 输出失败", cause);
        }

        String stdoutText = stdout.get().strip();
        String stderrText = stderr.get().strip();
        JsonNode response = parseResponse(stdoutText);
        boolean success = !timedOut && exitCode == 0;
        if (response != null && response.has("success") && response.get("success").isBoolean()) {
            success = success && response.get("success").asBoolean();
        }
        return new ChannelCliResult(success, exitCode, response, stdoutText, stderrText, timedOut, 1);
    }

    private static void logFailure(List<String> command, ChannelCliResult result) {
        if (result.success()) {
            return;
        }
        log.error(
                "腾讯频道 CLI 调用失败: command={}, exitCode={}, timedOut={}, attempts={}, error={}, stderr={}",
                commandName(command),
                result.exitCode(),
                result.timedOut(),
                result.attempts(),
                compactLogValue(result.getError().toString()),
                compactLogValue(result.stderr())
        );
    }

    private static String commandName(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "unknown";
        }
        if (command.size() > 1 && COMMAND_PART.matcher(command.get(1)).matches()) {
            return command.getFirst() + " " + command.get(1);
        }
        return command.getFirst();
    }

    private static String compactLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String compact = value.replace('\r', ' ').replace('\n', ' ').strip();
        int maxLength = 4000;
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }

    private List<String> buildPlatformCommand(List<String> arguments) {
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String normalizedPath = cliPath.toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win");
        boolean nativeExecutable = normalizedPath.endsWith(".exe");

        if (windows && !nativeExecutable) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add("call");
        }
        command.add(cliPath);
        command.addAll(arguments);
        return command;
    }

    private Thread readStream(InputStream stream, AtomicReference<String> destination,
                              AtomicReference<Throwable> failure, String streamName) {
        return Thread.ofVirtual().name("tencent-channel-cli-" + streamName).start(() -> {
            try (stream) {
                destination.set(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
    }

    private JsonNode parseResponse(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(stdout);
        } catch (Exception ignored) {
            String[] lines = stdout.lines().toArray(String[]::new);
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].strip();
                if (line.startsWith("{") || line.startsWith("[")) {
                    try {
                        return objectMapper.readTree(line);
                    } catch (Exception ignoredLine) {
                        // Keep looking for a machine-readable line.
                    }
                }
            }
            return null;
        }
    }

    private void ensureAvailable() {
        if (!enabled) {
            throw new ChannelCliException("tencent-channel-cli 功能未启用");
        }
        if (closed.get()) {
            throw new ChannelCliException("tencent-channel-cli 客户端已关闭");
        }
    }

    private static void validateCommandPart(String value, String name) {
        if (value == null || !COMMAND_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " contains unsupported characters: " + value);
        }
    }

    private static void joinReader(Thread reader) {
        try {
            reader.join(PROCESS_STOP_GRACE.toMillis());
            if (reader.isAlive()) {
                reader.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void terminateProcessTree(ProcessHandle root) {
        List<ProcessHandle> descendants = root.descendants().toList();
        for (int i = descendants.size() - 1; i >= 0; i--) {
            descendants.get(i).destroy();
        }
        root.destroy();
        try {
            root.onExit().get(PROCESS_STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            for (int i = descendants.size() - 1; i >= 0; i--) {
                ProcessHandle child = descendants.get(i);
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            }
            if (root.isAlive()) {
                root.destroyForcibly();
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (ProcessHandle process : List.copyOf(activeProcesses)) {
            terminateProcessTree(process);
        }
        activeProcesses.clear();
    }
}
