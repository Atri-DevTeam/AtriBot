package top.yzljc.sakuraba_ema.manager;

import top.yzljc.sakuraba_ema.ChannelCliClient;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChannelSystemClient {

    private final ChannelCliClient client;

    public ChannelSystemClient(ChannelCliClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public ChannelCliResult version() {
        return client.executeCommand(List.of("version"), null, ChannelCliOptions.DEFAULT, false);
    }

    public ChannelCliResult doctor() {
        return client.executeCommand(List.of("doctor"), null, ChannelCliOptions.DEFAULT, true);
    }

    public ChannelCliResult login() {
        return login(null, false);
    }

    public ChannelCliResult login(Path qrcodePath, boolean forceRelogin) {
        List<String> command = new ArrayList<>();
        command.add("login");
        if (qrcodePath != null) {
            command.add("--qrcode-path");
            command.add(qrcodePath.toAbsolutePath().normalize().toString());
        }
        ChannelCliOptions options = forceRelogin
                ? ChannelCliOptions.CONFIRMED
                : ChannelCliOptions.DEFAULT;
        return client.executeCommand(command, null, options, true);
    }

    public ChannelCliResult pollToken() {
        return client.executeCommand(List.of("login", "poll-token"), null,
                ChannelCliOptions.DEFAULT, true);
    }

    public ChannelCliResult loginStatus() {
        return client.executeCommand(List.of("login", "status"), null,
                ChannelCliOptions.DEFAULT, true);
    }

    public ChannelCliResult schema() {
        return client.executeCommand(List.of("schema"), null, ChannelCliOptions.DEFAULT, true);
    }

    public ChannelCliResult schema(String commandPath) {
        if (commandPath == null || !commandPath.matches("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("commandPath must use the domain.action form");
        }
        return client.executeCommand(List.of("schema", commandPath), null,
                ChannelCliOptions.DEFAULT, true);
    }

    public ChannelCliResult searchSchema(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        return client.executeCommand(List.of("schema", "--search", keyword), null,
                ChannelCliOptions.DEFAULT, true);
    }
}
