package top.yzljc.atribot.test;

import lombok.Getter;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.UserRunCommandEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName WhatFuckingPing
 * @Created_at 2026/08/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class WhatFuckingPing implements Listener {

    @Getter
    private static final Map<String, Long> accessMs = new HashMap<>();

    @EventHandler
    public void onCommandSend(UserRunCommandEvent event) {
        if (event.getCommandHeader().equals("boop") && event.getSender() instanceof QQCommandSender) {
            long currentTime = System.currentTimeMillis();
            accessMs.put(((QQCommandSender)event.getSender()).getMessage().getMessageId(), currentTime);
        }
    }
}