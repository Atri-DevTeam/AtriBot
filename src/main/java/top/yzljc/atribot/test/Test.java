package top.yzljc.atribot.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.Atri;
import top.yzljc.atricore.chat.official.*;
import top.yzljc.atricore.chat.official.button.*;
import top.yzljc.atricore.chat.official.card.Card;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.ConsoleCommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atricore.event.impl.AnswerCode;
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;
import top.yzljc.atricore.service.request.HttpService;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
@Slf4j
public class Test implements CommandExecutor, Listener {
    private static final AtomicBoolean SECURITY_BATCH_RUNNING = new AtomicBoolean();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission()) {
            sender.sendMessage("你是谁？");
            return true;
        }
        boolean securityBatch = args.length > 0 && "msg-sec-batch".equalsIgnoreCase(args[0]);
        if (securityBatch || args.length > 0 && "msg-sec".equalsIgnoreCase(args[0])) {
            if (securityBatch && !(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("批量测试仅支持控制台执行");
                return true;
            }
            String content = securityBatch && args.length == 1 ? "hello world!"
                    : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (content.isBlank()) {
                sender.sendMessage("用法：test msg-sec <待检测文本>，请将内容写在同一行");
                return true;
            }
            if (securityBatch && !SECURITY_BATCH_RUNNING.compareAndSet(false, true)) {
                sender.sendMessage("已有文本安全批量测试正在执行");
                return true;
            }
            try {
                ThreadManager.execute(() -> {
                    try {
                        testMsgSecurity(sender, content, securityBatch);
                    } finally {
                        if (securityBatch) SECURITY_BATCH_RUNNING.set(false);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                if (securityBatch) SECURITY_BATCH_RUNNING.set(false);
                sender.sendMessage("测试任务提交失败，请稍后重试");
            }
            return true;
        }
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("用法：test msg-sec <待检测文本> 或 test msg-sec-batch [待检测文本]");
            return true;
        }
//        if (args.length > 0) {
//            switch (args[0].toLowerCase()) {
//                case "-l" -> {
//                    sendChannelQueryResult(sender, ChannelInformation.getJoinedGuilds());
//                    return true;
//                }
//                case "-i" -> {
//                    if (args.length < 2) {
//                        sender.sendMessage("用法: /test -i <guildId>");
//                        return true;
//                    }
//                    sendChannelQueryResult(sender, ChannelInformation.getGuildInfo(args[1]));
//                    return true;
//                }
//                case "-p" -> {
//                    if (args.length < 2) {
//                        sender.sendMessage("用法: /test -p <guildId>");
//                        return true;
//                    }
//                    sendChannelQueryResult(sender, ChannelInformation.getChannelList(args[1]));
//                    return true;
//                }
//                default -> {
//                    // Keep the existing /test behavior for other arguments.
//                }
//            }
//        }
//        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
//        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
//        String url = RS.get("resource.audio.a-silent-mirror");
//        GroupChat.replyMessage(sender.getGroupId(), RT.message(sender.getMessageId()), 3, url);
//        String url = RS.get("resource.image.welcome");
//        Markdown md = TC.md(
//                "欢迎新人喵~\n\n" +
//                        Markdown.img(url, 1238 ,564) + "\n\n" + Markdown.link("https://hypixel.net/threads/add-an-achievement-or-something-for-clearing-the-cobwebs-in-the-haunted-biome.6129847", "查看原帖")
//        );
//        ChannelPosts.sendMessage("82565391648687862", "739210805", "Minecraft News!", md);
//        Object buttons = TC.promptKeyboard(
//                List.of(
//                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c2", "功能", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
//                )
//        );
//        ((QQCommandSender)sender).sendMessage(md, buttons);
//        for (int i = 0; i < 25; i++) {
//            GroupChat.sendMessage("38884BB0281B0641BBFCAE0BD12832CA", String.valueOf(i));
//        }
//        Markdown md = TC.md(Markdown.atAll());
//        sender.sendMessage(md);

//        String test = "你好";
//        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
//            String result = Atri.getInstance().getAiService().ask(AiProvider.OTHER, test);
//            sender.sendMessage(result);
//        });
//        Markdown md = TC.md(
//                "## 打卡成功\n\n" +
//                        "> 你已累计打卡**" + 1 + "**次！\n" +
//                        "> 今天已有**" + 2 + "**人参与了打卡！\n" +
//                        "> " + Markdown.img(RS.get("resource.image.gold"), 16, 16) + "+ " + 3 + "金粒" + "\n\n" +
//                        "文本123zzzzzzzzz"
//        );
//        Object buttons = TC.keyboard(List.of(
//                List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        sender.sendMessage(md, buttons);
//        var result = Atri.getInstance().getChatService().getUserInfo(sender.getUserId(), sender.getGroupId());
//        sender.sendMessage(result);
//        var md = TC.md(
//                Markdown.at(sender) + " 打卡成功\n\n" +
//                        ((d == null || d.url() == null) ? "" : Markdown.img(d.url(), d.w(), d.h()) + "\n\n") +
//                        "> 收集自网络，可联系删除 " + Markdown.enterCommand("/投稿 ", "我要投稿") + "\n" +
//                        "> 你已累计打卡**" + 20 + "**次！\n" +
//                        "> 今天已有**" + 11 + "**人参与了打卡！\n" +
//                        "> " + Markdown.img(RS.get("resource.image.gold"), 16, 16) + "+ " + 100 + "金粒   " + Markdown.enterCommand("/golds", "查看总数") + "\n\n"
//        );
//
//        Object buttons = TC.keyboard(List.of(
//                List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        String streamMessageId = C2CChat.replyStreamDeltas(sender.getUserId(), RT.message(sender.getMessageId()), List.of(
//                TC.md("正在生成回答..."),
//                TC.md("\n已完成标题部分"),
//                TC.md("\n这是最终内容")
//        ));
//        sender.sendMessage("消息ID: " + sender.getMessageId() + " 场景: " + sender.getPlatform());
//        if (args.length > 0 && args[0].equals("-g")) {
//            var t = LootService.drawFree(sender.getUserId());
//            sender.sendMessage(ImageComponent.imageOf(t.imageUrl()));
//            System.out.println("已为用户 " + sender.getUserId() + " 生成免费抽奖卡片");
//            return true;
//        }
//
//        String url = LootService.renderOverviewCard(sender.getUserId());
//        System.out.println(url);
//        sender.sendMessage(ImageComponent.imageOf(url));
//        sender.sendMessage(ImageComponent.imageOf("https://thirdqq.qlogo.cn/g?b=oidb&k=9ibwZcgtYsVOkxNVvIbaeSg&kti=adPQXgwBHsE&s=0&t=1775489118"));
//        ImageSourceClient.migrateUnreviewedToDirs();

//        var user = (QQCommandSender) sender;
//        Markdown md = TC.md("1");
//        Object btn1 = TC.keyboard(List.of(
//                List.of(new Button("c1", "按钮1", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND)),
//                List.of(new Button("c2", "按钮2", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ), ButtonSize.SMALL);
//        Object btn2 = TC.keyboard(List.of(
//                List.of(new Button("c1", "按钮1", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND)),
//                List.of(new Button("c2", "按钮2", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        user.sendMessage(md, btn1);
//        user.sendMessage(md, btn2);
//        if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).startsWith("ark")) {
//            if (!(sender instanceof QQCommandSender qq)
//                    || (qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C)) {
//                sender.sendMessage("Ark 测试仅支持 QQ 官方群聊和私聊");
//                return true;
//            }
//
//            Ark ark = switch (args[0].toLowerCase(Locale.ROOT)) {
//                case "ark23" -> Ark.ark23("标题", "内容", List.of(
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        Ark23.Item.text("描述"),
//                        new Ark23.Item("描述2", jumpUrl)
//                ));
//                case "ark24" -> Ark.ark24("描述", "内容", "标题", "描述2", imageUrl, jumpUrl, "子标题");
//                case "ark37" -> Ark.ark37("内容", "标题", "子标题", imageUrl, jumpUrl);
//                default -> null;
//            };
//            if (ark == null) {
//                sender.sendMessage("用法: /test <ark23|ark24|ark37>");
//                return true;
//            }
//
//            // Ark 测试使用主动消息，不携带当前命令的消息 ID。
//            if (qq.getPlatform() == Platform.OFFICIAL_GROUP) {
//                GroupChat.sendMessage(qq.getGroupId(), ark);
//            } else {
//                C2CChat.sendMessage(qq.getUserId(), ark);
//            }
//            return true;
//        }
        var u = (QQCommandSender) sender;
//        GroupChat.replyMessage(u.getGroupId(), RT.message(u.getMessage().getMessageId()), d);
//        u.sendMessage(md.setKeyboard(
//                new Keyboard(                List.of(
//                        List.of(new Button("c1", "按钮1", "按钮1-data", true, ButtonStyle.RED, ButtonType.COMMAND)),
//                        List.of(new Button("c2", "按钮2", "按钮2-data", true, ButtonStyle.GRAY, ButtonType.COMMAND)),
//                        List.of(new Button("c3", "按钮3", "按钮3-data", true, ButtonStyle.ICON_BUTTON, ButtonType.COMMAND)),
//                        List.of(new Button("c4", "按钮4", "按钮4-data", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND))
//                        )).setPromptStyle(true)
//        ));
//        u.sendMessage(md.setKeyboard(new Keyboard(List.of(List.of(new Button("c1", "按钮1", "按钮1-data", true, ButtonStyle.RED, ButtonType.CUSTOM).setUnsupportedTip("你好老大"))))));
//        GroupChat.sendMessage(u.getGroupId(), Card.tuWen(
//        ));
//        u.sendMessage(md.setKeyboard(new Keyboard(
//                List.of(
//                        List.of(
//                                new Button("c1", "Test - callback", "data", true, ButtonStyle.GRAY, ButtonType.CALLBACK)
//                        )
//                )
//        )));
//        d();
        var md = TC.md("**亚托莉喵反馈调研**\n\n" +
                "> 你好呀，我是亚托莉喵～想听听你的真实想法喵！\n" + "> 完成本次小调研，就能获得 300 金粒 奖励哦～\n> 请根据真实感受作答，你的每一条反馈我都会认真记下来的喵！\n> 谢谢你的帮忙，快来帮帮亚托莉喵吧～\n\n" +
                Markdown.link("https://v.wjx.cn/vm/hNCP76y.aspx#", "点击参与调研"));
//        if (args.length == 1 && args[0].equalsIgnoreCase("retry-active")) {
//            try {
//                C2CActiveMessageRetry.start(md);
//                sender.sendMessage("已开始向固定名单中的 418 名用户补发主动消息，进度请查看控制台日志");
//            } catch (IllegalStateException e) {
//                sender.sendMessage(e.getMessage());
//            }
//            return true;
//        }
        u.sendMessage(md);
        sender.sendMessage("已发送调研问卷，请在私聊中完成喵~");

        return true;
    }

    private static void testMsgSecurity(CommandSender sender, String content, boolean batch) {
        try {
            String appId = "x";
            String secret = "y";
            var mapper = new ObjectMapper();
            var tokenRequest = HttpService.newRequestBuilder()
                    .uri(URI.create("https://api.q.qq.com/api/getToken?grant_type=client_credential&appid="
                            + URLEncoder.encode(appId, StandardCharsets.UTF_8) + "&secret="
                            + URLEncoder.encode(secret, StandardCharsets.UTF_8)))
                    .GET().build();
            var tokenResponse = HttpService.httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode tokenResult = mapper.readTree(tokenResponse.body());
            String token = tokenResult == null ? "" : tokenResult.path("access_token").asText("");
            if (tokenResponse.statusCode() < 200 || tokenResponse.statusCode() >= 300 || token.isBlank()) {
                reportSecurityTest(sender, "QQ 小程序 Token 获取失败", tokenResponse, secret, token);
                return;
            }
            log.info("QQ 小程序 Token 获取成功: expires_in={}", tokenResult.path("expires_in").asLong());
            sender.sendMessage("QQ 小程序 Token 获取成功，正在测试文本安全接口");
            String body = mapper.writeValueAsString(Map.of("appid", appId, "content", content));
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("实际提交 content：" + mapper.writeValueAsString(content)
                        + "\nUTF-8 字节数：" + content.getBytes(StandardCharsets.UTF_8).length);
            }
            var request = HttpService.newRequestBuilder()
                    .uri(URI.create("https://api.q.qq.com/api/json/security/MsgSecCheck?access_token="
                            + URLEncoder.encode(token, StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            if (batch) {
                testMsgSecurityBatch(sender, request, mapper, secret, token);
                return;
            }
            var response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            reportSecurityTest(sender, "文本安全接口测试", response, secret, token);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.sendMessage("文本安全接口测试已中断");
        } catch (Exception e) {
            log.warn("QQBot 文本安全接口测试失败: type={}", e.getClass().getSimpleName());
            sender.sendMessage("文本安全接口测试失败: " + e.getClass().getSimpleName());
        }
    }

    private static void testMsgSecurityBatch(CommandSender sender, HttpRequest request, ObjectMapper mapper,
                                             String secret, String token) throws InterruptedException {
        int total = 100;
        int concurrency = 10;
        sender.sendMessage("开始文本安全批量测试：100 次请求，并发 10，复用同一个 Token，不自动重试");
        var counts = new ConcurrentSkipListMap<String, AtomicInteger>();
        long started = System.nanoTime();
        var workers = Executors.newFixedThreadPool(concurrency,
                Thread.ofVirtual().name("security-test-", 0).factory());
        try (workers) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 1; i <= total; i++) {
                int number = i;
                futures.add(workers.submit(() -> {
                    long requestStarted = System.nanoTime();
                    String category;
                    try {
                        var response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        String body = response.body().replace(secret, "[REDACTED]").replace(token, "[REDACTED]");
                        if (body.length() > 512) body = body.substring(0, 512) + "…";
                        category = "HTTP " + response.statusCode();
                        try {
                            JsonNode result = mapper.readTree(response.body());
                            if (result != null) {
                                JsonNode code = result.has("errCode") ? result.get("errCode") : result.get("errcode");
                                category += " / errCode " + (code == null ? "缺失" : code.asText());
                            }
                        } catch (java.io.IOException e) {
                            category += " / 非 JSON 响应";
                        }
                        log.info("文本安全批量测试: {}/{}, HTTP={}, elapsedMs={}, response={}", number, total,
                                response.statusCode(), (System.nanoTime() - requestStarted) / 1_000_000, body);
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        category = "请求异常 / " + e.getClass().getSimpleName();
                        log.warn("文本安全批量测试: {}/{}, type={}", number, total, e.getClass().getSimpleName());
                    }
                    counts.computeIfAbsent(category, ignored -> new AtomicInteger()).incrementAndGet();
                }));
            }
            for (var future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    counts.computeIfAbsent("任务异常", ignored -> new AtomicInteger()).incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            throw e;
        }
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        StringBuilder summary = new StringBuilder("文本安全批量测试结束\n请求：100，并发：10，Token 获取：1 次\n")
                .append(String.format(java.util.Locale.ROOT, "总耗时：%.3f 秒，平均吞吐：%.2f 次/秒\n", seconds, total / seconds));
        counts.forEach((category, count) -> summary.append(category).append("：").append(count.get()).append('\n'));
        sender.sendMessage(summary.toString());
    }

    private static void reportSecurityTest(CommandSender sender, String stage, HttpResponse<String> response, String... secrets) {
        String result = response.body();
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                result = result.replace(secret, "[REDACTED]")
                        .replace(URLEncoder.encode(secret, StandardCharsets.UTF_8), "[REDACTED]");
            }
        }
        if (result.length() > 4096) result = result.substring(0, 4096) + "…";
        log.info("{}: HTTP={}, response={}", stage, response.statusCode(), result);
        sender.sendMessage(stage + "\nHTTP: " + response.statusCode() + "\n" + result);
    }

    private static Keyboard getMiniGames() {
        return new Keyboard(
                List.of(
                        List.of(
                                new Button("bw", "起床", "/hyp gs bw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("sw", "空岛", "/hyp gs sw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("arc", "街机", "/hyp gs arc", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("pit", "决斗", "/hyp gs duel", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("skb", "Skyblock", "/hyp gs skb", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mm", "密室杀手", "/hyp gs mm", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("bb", "建筑大师", "/hyp gs bb", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("wool", "羊毛游戏", "/hyp gs wool", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("tnt", "TNT游戏", "/hyp gs tnt", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("classic", "经典游戏", "/hyp gs classic", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("bsg", "饥饿游戏", "/hyp gs bsg", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("ptl", "实验大厅", "/hyp gs ptl", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mw", "超级战墙", "/hyp gs mw", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("global", "全部小游戏", "/hyp gs", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );
    }

    private static void sendChannelQueryResult(CommandSender sender, ChannelCliResult result) {
        if (result.success()) {
            sender.sendMessage(result.getData().toPrettyString());
            return;
        }
        sender.sendMessage("查询失败: " + result.getError().toString());
    }
}
