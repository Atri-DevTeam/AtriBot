package top.yzljc.atribot.function.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonSize;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.GroupRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMemberAddEvent;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.qq.QQBot;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupJoinWelcome
 * @Created_at 2026/08/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
@Slf4j
public class GroupJoinWelcome implements Listener, CommandExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 群是否配置了个性化入群欢迎（文本与键盘任一存在即视为已配置）
     */
    public static boolean hasCustomWelcome(String groupOpenId) {
        ObjectNode config = loadWelcomeConfig(groupOpenId);
        if (config == null) {
            return false;
        }
        if (!config.path("text").asText("").isBlank()) {
            return true;
        }
        JsonNode keyboard = config.path("keyboard");
        return keyboard.isArray() && !keyboard.isEmpty();
    }

    /**
     * 获取群个性化入群欢迎的文本（markdown）部分，未配置返回 null
     */
    public static String getWelcomeText(String groupOpenId) {
        ObjectNode config = loadWelcomeConfig(groupOpenId);
        if (config == null) {
            return null;
        }
        String text = config.path("text").asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * 获取群个性化入群欢迎的键盘部分：把存储的按钮二维数组重建为 Button 并经
     * TC.keyboard 序列化，返回可直接传给 sendMessage 的 keyboard 对象；未配置返回 null
     */
    public static Object getWelcomeKeyboard(String groupOpenId) {
        ObjectNode config = loadWelcomeConfig(groupOpenId);
        if (config == null) {
            return null;
        }

        JsonNode keyboardNode = config.path("keyboard");
        if (!keyboardNode.isArray() || keyboardNode.isEmpty()) {
            return null;
        }

        List<List<Button>> layout = new ArrayList<>();
        for (JsonNode rowNode : keyboardNode) {
            if (!rowNode.isArray() || rowNode.isEmpty()) {
                continue;
            }
            List<Button> row = new ArrayList<>();
            for (JsonNode btnNode : rowNode) {
                if (btnNode.isObject()) {
                    row.add(deserializeButton(btnNode));
                }
            }
            layout.add(row);
        }
        if (layout.isEmpty()) {
            return null;
        }

        ButtonSize size = parseEnum(config.path("button_size").asText(""), ButtonSize.class, ButtonSize.UNDEFINED);
        return TC.keyboard(layout, size);
    }

    /**
     * 保存群个性化入群欢迎配置（文本 + 按钮二维数组，键盘字号默认）
     */
    public static boolean saveCustomWelcome(String groupOpenId, String text, List<List<Button>> keyboard) {
        return saveCustomWelcome(groupOpenId, text, keyboard, null);
    }

    /**
     * 保存群个性化入群欢迎配置。text 与 keyboard 至少提供一项；
     * keyboard 为按钮二维数组（外层为行，内层为该行的按钮），size 为键盘整体字号，null 视为默认
     */
    public static boolean saveCustomWelcome(String groupOpenId, String text, List<List<Button>> keyboard, ButtonSize size) {
        boolean hasText = text != null && !text.isBlank();
        boolean hasKeyboard = keyboard != null && !keyboard.isEmpty();
        if (!hasText && !hasKeyboard) {
            return false;
        }

        ObjectNode config = MAPPER.createObjectNode();
        if (hasText) {
            config.put("text", text);
        }
        if (size != null && size != ButtonSize.UNDEFINED) {
            config.put("button_size", size.name());
        }
        if (hasKeyboard) {
            ArrayNode rows = config.putArray("keyboard");
            for (List<Button> row : keyboard) {
                if (row == null) {
                    continue;
                }
                ArrayNode rowNode = MAPPER.createArrayNode();
                for (Button btn : row) {
                    if (btn != null) {
                        rowNode.add(serializeButton(btn));
                    }
                }
                if (!rowNode.isEmpty()) {
                    rows.add(rowNode);
                }
            }
            if (rows.isEmpty()) {
                config.remove("keyboard");
            }
        }

        try {
            return GroupRepository.saveJoinWelcomeConfigJson(groupOpenId, MAPPER.writeValueAsString(config));
        } catch (Exception e) {
            log.error("保存群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 清除群个性化入群欢迎配置（恢复默认欢迎语）
     */
    public static boolean clearCustomWelcome(String groupOpenId) {
        return GroupRepository.deleteJoinWelcomeConfig(groupOpenId);
    }

    private static ObjectNode loadWelcomeConfig(String groupOpenId) {
        String json = GroupRepository.getJoinWelcomeConfigJson(groupOpenId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception e) {
            log.error("解析群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
        }
        return null;
    }

    private static ObjectNode serializeButton(Button btn) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("button_id", btn.getButtonId());
        node.put("display_text", btn.getDisplayText());
        if (btn.getVisitedDisplayText() != null && !btn.getVisitedDisplayText().equals(btn.getDisplayText())) {
            node.put("visited_display_text", btn.getVisitedDisplayText());
        }
        node.put("data", btn.getData());
        node.put("enter", btn.isEnter());
        if (btn.isReply()) {
            node.put("reply", true);
        }
        node.put("style", btn.getStyle().name());
        node.put("type", btn.getActionType().name());
        if (btn.getPermissionType() != PermissionType.ALL) {
            node.put("permission", btn.getPermissionType().name());
        }
        if (btn.getPermissionType() == PermissionType.SPECIFIC_USER && !btn.getAllowedOpenIds().isEmpty()) {
            ArrayNode ids = node.putArray("allowed_open_ids");
            btn.getAllowedOpenIds().forEach(ids::add);
        }
        if (btn.getModal() != null && btn.getModal().getContent() != null
                && !btn.getModal().getContent().equals(Identifier.UNDEFINED)) {
            ObjectNode modal = node.putObject("modal");
            modal.put("content", btn.getModal().getContent());
            if (btn.getModal().getConfirmText() != null) {
                modal.put("confirm_text", btn.getModal().getConfirmText());
            }
            if (btn.getModal().getCancelText() != null) {
                modal.put("cancel_text", btn.getModal().getCancelText());
            }
        }
        return node;
    }

    private static Button deserializeButton(JsonNode node) {
        Button btn = new Button(
                node.path("button_id").asText(""),
                node.path("display_text").asText(""),
                node.path("data").asText(""),
                node.path("enter").asBoolean(true),
                parseEnum(node.path("style").asText(), ButtonStyle.class, ButtonStyle.BLUE),
                parseEnum(node.path("type").asText(), ButtonType.class, ButtonType.COMMAND));

        JsonNode visited = node.path("visited_display_text");
        if (visited.isTextual()) {
            btn.setVisitedDisplayText(visited.asText());
        }
        if (node.path("reply").asBoolean(false)) {
            btn.setReply(true);
        }
        btn.setPermissionType(parseEnum(node.path("permission").asText("ALL"), PermissionType.class, PermissionType.ALL));

        JsonNode ids = node.path("allowed_open_ids");
        if (ids.isArray() && !ids.isEmpty()) {
            List<String> allowed = new ArrayList<>();
            ids.forEach(id -> allowed.add(id.asText()));
            btn.setAllowedOpenIds(allowed);
        }

        JsonNode modal = node.path("modal");
        if (modal.isObject() && modal.path("content").isTextual()) {
            String confirm = modal.path("confirm_text").asText(null);
            String cancel = modal.path("cancel_text").asText(null);
            if (confirm != null && cancel != null) {
                btn.setModal(modal.path("content").asText(), confirm, cancel);
            } else {
                btn.setModal(modal.path("content").asText());
            }
        }
        return btn;
    }

    private static <E extends Enum<E>> E parseEnum(String name, Class<E> type, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            log.warn("入群欢迎配置中的枚举值 {} 无法解析为 {}，使用默认 {}", name, type.getSimpleName(), fallback);
            return fallback;
        }
    }

    @EventHandler
    public void onMemberJoin(OfficialGroupMemberAddEvent event) {
        // 成员入群后刷新一次所在群资料（成员数等）
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> QQEventRecord.fetchAndSaveGroupProfile(event.getGroupOpenId()));
        log.info("[!] 成员入群，群资料刷新，群ID {}, 用户ID {}", event.getGroupOpenId(), event.getMemberOpenId());
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            if (event.getGroupOpenId().equals("8B4709F81FE02E5E64AC31B2F910793A")) {
                if (CoinGainLogRepository.countCoinGains(event.getMemberOpenId(), "join_my_group") < 1) {
                    LootRepository.addCoins(event.getMemberOpenId(), 100, "join_my_group");
                    log.info("Added coins to new member {} for joining group {} (join_my_group)", event.getMemberOpenId(), event.getGroupOpenId());
                }
            }
        });
        if (!OfficialGroups.isFunctionEnabled(event.getGroupOpenId(), "member_add_welcome")) {
            return;
        }

        if (!hasCustomWelcome(event.getGroupOpenId())) {
            // Guided by GordonHim
            String url = ResourcesProperties.WELCOME_IMG;
            String welStr = "欢迎新人喵~";
            int width = 1238;
            int height = 564;
            if (OfficialUsers.getRole(event.getMemberOpenId()) == UnifiedRole.OWNER) {
                welStr = "欢迎" + QQBot.BOT_NAME + "开发者YZ_Ljc_加入本群，有关机器人的问题可以随时与我联系，感谢各位支持喵~";
                url = ResourcesProperties.WELCOME_DEV_IMG;
                width = 850;
                height = 479;
            } else if (OfficialUsers.getRole(event.getMemberOpenId()) == UnifiedRole.ADMIN) {
                welStr = "欢迎" + QQBot.BOT_NAME + "管理员加入本群，有关机器人的问题可以随时与我联系，感谢各位支持喵~";
            }
            Markdown md = TC.md(
                    Markdown.at(event.getMemberOpenId()) + " " + welStr + "\n\n" +
                            Markdown.img(url, width, height) + "\n\n" +
                            "> " + Markdown.enterCommand("/tasks disable member_add_welcome", "关闭欢迎提示")
            );
            Object buttons = TC.keyboard(
                    List.of(
                            List.of(new Button("c1", "打卡", "/sign", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                    new Button("c2", "帮助", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                    new Button("c3", "自定义欢迎", "/how-to-custom-text ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    ), ButtonSize.SMALL
            );
            event.sendMessage(md, buttons);
        } else {
            String text = getWelcomeText(event.getGroupOpenId());
            Object keyboard = getWelcomeKeyboard(event.getGroupOpenId());
            if (text != null) {
                String message = Markdown.at(event.getMemberOpenId()) + " " + text;
                event.sendMessage(TC.md(message), keyboard);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String text = "**设置个性化欢迎**\n\n" +
                "群主或群管理员可以申请为本群设置个性化欢迎文本，包括图片，markdown文本，按钮等bot拥有的能力，如有相关需求请点击" +
                Markdown.enterCommand("/feedback 申请自定义欢迎文本", "申请获取") + "联系开发者制作！";
        if (sender instanceof QQCommandSender user) {
            user.sendMessage(TC.md(text));
        }
        return true;
    }
}