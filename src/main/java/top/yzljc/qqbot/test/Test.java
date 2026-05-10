package top.yzljc.qqbot.test;

import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.service.ai.AiService;
import top.yzljc.qqbot.service.thread.ThreadManager;

import java.util.concurrent.CompletableFuture;

public class Test implements Listener {

    private final AiService aiService = AtriBot.getInstance().getAiService();

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
                        event.getSender().replay(answer);
                    })
                    .exceptionally(ex -> {
                        event.getSender().replay("亚托莉的脑回路好像卡住了……请稍后再试呀！我是高性能的嘛！");
                        return null;
                    });
        }
    }
}