package top.yzljc.qqbot.official.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.OfficialPrivateChatEvent;
import top.yzljc.qqbot.official.service.QQBotMessageService;

/**
 * @Author YZ_Ljc_
 * @ClassName AccountInfo
 * @Created_at 2026/05/07
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.impl
 */
@Component
public class AccountInfo implements Listener, CommandExecutor {

    @Autowired
    private QQBotMessageService service;

    @PostConstruct
    public void init() {
        EventManager.getInstance().registerEvents(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, int label, String[] args) {
        if (label != 1) return true;
        service.replyPrivateTextMessage(sender.userOpenId(), sender.messageOpenId(), sender.userOpenId());
        System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        return false;
    }

//    @EventHandler
//    public void onChat(OfficialPrivateChatEvent event) {
//        service.replyPrivateTextMessage(event.getOpenId(), event.getMsgId(), event.getContent());
//    }
}