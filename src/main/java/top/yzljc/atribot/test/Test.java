package top.yzljc.atribot.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.Keyboard;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.event.impl.OfficialInteractionEvent;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.service.official.CommandButton;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Test implements Listener, CommandExecutor {

    private final AiService aiService = Atri.getInstance().getAiService();

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
//        Object k = Atri.getInstance().getChatService().buildCmdKeyboard(List.of(
//                List.of(new CommandButton("c1", "样式0", "/help", false, 0, 2),
//                        new CommandButton("c2", "样式1", "/help", false, 1, 2)
//                ),
//                List.of(new CommandButton("c3", "样式2", "/help", false, 2, 2),
//                        new CommandButton("c4", "样式3", "/help", false, 3, 2)
//                ),
//                List.of(new CommandButton("c5", "样式4", "/help", false, 4, 2))
//        ));
        Object k = Keyboard.build(
                List.of(
                        List.of(new CommandButton("c1", "成功", "callback_data", true, 0, 1),
                                new CommandButton("c2", "失败", "callback_data", true, 1, 1),
                                new CommandButton("c3", "过快", "callback_data", true, 2, 1)
                        ),
                        List.of(new CommandButton("c4", "重复", "callback_data", true, 3, 1),
                                new CommandButton("c5", "无权限", "callback_data", true, 4, 1),
                                new CommandButton("c6", "仅管理员", "callback_data", true, 5, 1)
                        ))
        );
        sender.replyMarkdown(label, TC.md("markdown"), k);
//
//        List<Map<String, Object>> list = List.of(
//                Map.of("key", Ark.KEY_DESC,      "value", "机器人订阅消息"),
//                Map.of("key", Ark.KEY_PROMPT,    "value", "XX机器人"),
//                Map.of("key", Ark.KEY_TITLE,     "value", "标题"),
//                Map.of("key", Ark.KEY_META_DESC, "value", "meta描述"),
//                Map.of("key", Ark.KEY_META_URL,  "value", "https://example.com"),
//                Map.of("key", Ark.KEY_META_LIST, "obj", List.of(
//                        Map.of("obj_kv", List.of(
//                                Ark.pair("name", "aaa"),
//                                Ark.pair("age", "3")
//                        )),
//                        Map.of("obj_kv", List.of(
//                                Ark.pair("name", "bbb"),
//                                Ark.pair("age", "4")
//                        ))
//                ))
//        );
//
//        Ark ark = TC.ark(Ark.TEMPLATE_LINK_TEXT_LIST, list);
//        GroupChat.sendMessage(sender.groupOpenId(), ark);
        return true;
    }

    public void callback(OfficialInteractionEvent event) {
        if (event.getType() == 11) {
            if (event.getData().getResolved().path("button_id").asText().equals("c1")) {
                event.answer(AnswerCode.SUCCESS);
            }
            if (event.getData().getResolved().path("button_id").asText().equals("c2")) {
                event.answer(AnswerCode.FAIL);
            }
            if (event.getData().getResolved().path("button_id").asText().equals("c3")) {
                event.answer(AnswerCode.TOO_FAST);
            }
            if (event.getData().getResolved().path("button_id").asText().equals("c4")) {
                event.answer(AnswerCode.REPEAT);
            }
            if (event.getData().getResolved().path("button_id").asText().equals("c5")) {
                event.answer(AnswerCode.NO_PERMISSION);
            }
            if (event.getData().getResolved().path("button_id").asText().equals("c6")) {
                event.answer(AnswerCode.ONLY_ADMIN);
            }
        }
        event.sendMessage("收到callback: " + event.getData().getResolved().toString());
    }
}