package top.yzljc.atribot.test;

import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.UserRunCommandEvent;

import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.qq.QQConnectionLatency;

/**
 * @Author YZ_Ljc_
 * @ClassName WhatFuckingPing
 * @Created_at 2026/08/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class WhatFuckingPing implements Listener {

    @EventHandler
    public void onCommandSend(UserRunCommandEvent event) {
        if ("boop".equals(event.getCommandHeader()) && event.getSender() instanceof QQCommandSender sender) {
            Platform platform = sender.getPlatform();
            if (platform == Platform.OFFICIAL_GROUP || platform == Platform.OFFICIAL_C2C) {
                QQConnectionLatency.commandStarted(platform == Platform.OFFICIAL_GROUP ? "群聊" : "单聊",
                        sender.getMessage().getMessageId());
            }
        }
    }
}