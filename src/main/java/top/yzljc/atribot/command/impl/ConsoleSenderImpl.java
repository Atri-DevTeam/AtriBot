package top.yzljc.atribot.command.impl;

import top.yzljc.atribot.command.ConsoleCommandSender;
import top.yzljc.atribot.service.runtime.ConsoleManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ConsoleSenderImpl implements ConsoleCommandSender {

    private static final String PREFIX = "\u001B[36m[atri]\u001B[0m ";

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public String getUserId() {
        return "console";
    }

    @Override
    public String getUsername() {
        return "Console";
    }

    @Override
    public boolean hasPermission() {
        return true;
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public String sendMessage(String text) {
        ConsoleManager.printConsole(getTimePrefix() + PREFIX + text + "\n");
        return text;
    }

    private String getTimePrefix() {
        String RESET = "\u001B[0m";
        String GREEN = "\u001B[92m";
        return GREEN + "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + RESET;
    }
}
