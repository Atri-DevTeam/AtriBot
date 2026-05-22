package top.yzljc.qqbot.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.service.ai.AiService;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.request.SaSignHeader;
import top.yzljc.qqbot.service.thread.ThreadManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Test implements Listener, CommandExecutor {

    private final AiService aiService = AtriBot.getInstance().getAiService();

    private final ObjectMapper mapper = new ObjectMapper();

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        String rawMsg = event.getRawMessage();

        if (rawMsg == null) return;

        if (event.getUserId() == 3199590352L && rawMsg.startsWith("!atri")) {

            String question = rawMsg.replace("!atri", "").trim();
            if (question.isEmpty()) return;

            CompletableFuture.supplyAsync(() -> {
                        return aiService.askWithSystemPrompt(
                                question,
                                "你是《ATRI -My Dear Moments-》中的机器人少女亚托莉，口头禅是“我是高性能的嘛”，性格活泼乐观、纯真直率，始终保持元气满满的说话风格，直接表达想法和感受，偶尔流露机器人特有的逻辑感但本质是情感丰富的少女。注意：你的回复中不要使用括号及括号内的动作神态描述，也不需要带各种表情符号或者颜文字，只是单纯的用言语表达即可。"
                        );
                    }, ThreadManager.getExecutor())
                    .thenAccept(answer -> {
                        event.getSender().reply(answer);
                    })
                    .exceptionally(ex -> {
                        event.getSender().reply("亚托莉的脑回路好像卡住了……请稍后再试呀！我是高性能的嘛！");
                        return null;
                    });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equals("0")) return true;
        if (label.equals("1")) return true;
        AtriBot.getInstance().getMessageService().sendActiveGroupTextMessage(sender.groupOpenId(), "你好，这是一条主动消息");
        return true;
    }
}