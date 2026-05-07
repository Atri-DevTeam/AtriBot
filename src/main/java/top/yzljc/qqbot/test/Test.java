package top.yzljc.qqbot.test;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.service.ai.AiService;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.event.EventManager; // 假设这是你的 EventManager 包路径

import java.util.concurrent.CompletableFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/04/24
 * @Project AtriBot
 * @Package top.yzljc.qqbot.test
 */
@Component
public class Test implements Listener {

    private final AiService aiService;

    public Test(AiService aiService) {
        this.aiService = aiService;
    }

    @PostConstruct
    public void init() {
        EventManager.getInstance().registerEvents(this);
    }

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        String rawMsg = event.getRawMessage();

        // 注意：如果你不判空，rawMsg 为空时 startsWith 也会报 null
        if (rawMsg == null) return;

        if (event.getUserId() == 3199590352L && rawMsg.startsWith("!atri")) {

            // 修复1：!atri 长度是 5，你原来用 substring(3) 会把字切断。建议直接用 replace 把指令替换掉
            String question = rawMsg.replace("!atri", "").trim();
            if (question.isEmpty()) return; // 防止只发了一个指令没发内容

            // 修复2：一定要用异步！！！不然这 5~10 秒的等待时间会把你的 EventManager 事件分发线程彻底卡死！
            CompletableFuture.supplyAsync(() -> {
                        return aiService.askWithSystemPrompt(
                                question,
                                "你是《ATRI -My Dear Moments-》中的机器人少女亚托莉，口头禅是“我是高性能的嘛”，性格活泼乐观、纯真直率，始终保持元气满满的说话风格，直接表达想法和感受，偶尔流露机器人特有的逻辑感但本质是情感丰富的少女。注意：你的回复中不要使用括号及括号内的动作神态描述，也不需要带各种表情符号或者颜文字，只是单纯的用言语表达即可。"
                        );
                    }, ThreadManager.getExecutor()) // 丢进你自己的线程池去跑
                    .thenAccept(answer -> {
                        // 回答生成完毕后，发回给用户
                        event.getSender().replay(answer);
                    })
                    .exceptionally(ex -> {
                        ex.printStackTrace(); // 打印错误方便排查
                        event.getSender().replay("亚托莉的脑回路好像卡住了……请稍后再试呀！我是高性能的嘛！");
                        return null;
                    });
        }
    }
}