package top.yzljc.atribot.miniapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.management.GroupMember;
import top.yzljc.atribot.chat.official.moderation.*;
import top.yzljc.atribot.database.repo.GroupBindingRepository;
import top.yzljc.atribot.miniapp.MiniappSessions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static top.yzljc.atribot.miniapp.service.MiniappGroupService.Problem;

/** 群主自助配置仅接受受限字段；原始规则始终保留在服务端。 */
public final class MiniappModerationService {
    public static final String REMINDER = "喵~这种内容可不行哦，换个话题吧~";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final GroupBindingRepository bindings;
    private final BiFunction<String, String, GroupMember.MemberInfo> members;
    private final Predicate<String> promptPermission;
    private final Predicate<String> moderationPermission;
    private final Predicate<String> blocked;
    private final Store store;
    private final byte[] revisionKey = new byte[32];

    public interface Store {
        GroupModerationSettings read(String groupId);
        GroupModerationSettings update(String groupId, UnaryOperator<GroupModerationSettings> edit);
    }

    public MiniappModerationService() {
        this(new GroupBindingRepository(), GroupMember::getMemberInfo,
                user -> hasPermission(user, "group.moderation"),
                user -> hasPermission(user, "atri.custom_prompt"), OfficialUsers::isBlocked, new Store() {
                    public GroupModerationSettings read(String groupId) { return GroupModerationStore.snapshot(groupId); }
                    public GroupModerationSettings update(String groupId, UnaryOperator<GroupModerationSettings> edit) {
                        return GroupModerationStore.update(groupId, edit);
                    }
                });
    }

    MiniappModerationService(GroupBindingRepository bindings,
                             BiFunction<String, String, GroupMember.MemberInfo> members,
                             Predicate<String> moderationPermission, Predicate<String> promptPermission, Predicate<String> blocked, Store store) {
        this.bindings = bindings;
        this.members = members;
        this.promptPermission = promptPermission;
        this.moderationPermission = moderationPermission;
        this.blocked = blocked;
        this.store = store;
        new SecureRandom().nextBytes(revisionKey);
    }

    public record ReadOnlyField(String label, String value) {}
    public record ReadOnlyRule(String title, List<ReadOnlyField> fields) {}
    public record Keyword(String id, String masked, MatchMode matchMode, boolean remind, List<ReadOnlyField> readOnly) {}
    public record Domain(String id, String masked) {}
    public record View(boolean canManage, boolean canCustomizePrompt, String revision,
                       boolean keywordEnabled, List<Keyword> keywords, boolean aiEnabled, int aiType, boolean aiRemind,
                       boolean allowAllLinks, List<Domain> domains, boolean promptConfigured,
                       List<ReadOnlyField> aiReadOnly, List<ReadOnlyRule> otherRules, String customPrompt,
                       AiModerationSchedule aiSchedule) {}

    public boolean canManage(MiniappSessions.Identity identity) {
        return !blocked.test(identity.userId()) && moderationPermission.test(identity.userId());
    }

    /** 与 User.hasPermission(String) 的官方账号权限判定保持一致。 */
    static boolean hasPermission(String userId, String permission) {
        return OfficialUsers.isAdmin(userId) || OfficialUsers.hasPermission(userId, permission);
    }

    public View load(MiniappSessions.Identity identity, String groupId, Runnable checkSession) throws SQLException {
        authorize(identity, groupId);
        checkSession.run();
        return view(identity, groupId, store.read(groupId));
    }

    public View save(MiniappSessions.Identity identity, String groupId, JsonNode request, Runnable checkSession) throws SQLException {
        authorize(identity, groupId);
        fields(request, Set.of("revision", "keywordEnabled", "keywords", "aiEnabled", "aiType", "aiRemind", "allowAllLinks", "domains", "customPrompt", "aiSchedule"));
        checkSession.run();
        var saved = store.update(groupId, current -> {
            checkSession.run();
            if (!canManage(identity)) throw new Problem(403, "MODERATION_FORBIDDEN");
            if (request.has("customPrompt") && !promptPermission.test(identity.userId()))
                throw new Problem(403, "CUSTOM_PROMPT_FORBIDDEN");
            if (!revision(groupId, current).equals(string(request, "revision", 100, false)))
                throw new Problem(409, "MODERATION_CHANGED");
            GroupModerationSettings next = defaults(current);
            var keywords = next.getKeywordRecall();
            List<ViolationRule> previous = keywords.getRules();
            List<ViolationRule> rules = new ArrayList<>();
            Set<String> used = new HashSet<>();
            for (JsonNode entry : array(request, "keywords")) {
                fields(entry, Set.of("id", "value", "matchMode", "remind"));
                ViolationRule rule;
                ModerationAction existingAction = null;
                if (entry.has("id")) {
                    int index = index(entry, previous.size(), used);
                    rule = previous.get(index);
                    if (rule == null || rule.getType() != ViolationRuleType.KEYWORD) throw invalid();
                    existingAction = effectiveAction(rule, keywords);
                } else {
                    rule = new ViolationRule();
                    rule.setRuleId(UUID.randomUUID().toString());
                    rule.setKeyword(string(entry, "value", 256, false));
                }
                String mode = string(entry, "matchMode", 16, false);
                if (!mode.equals("CONTAINS") && !mode.equals("EQUALS")) throw invalid();
                rule.setMatchMode(MatchMode.valueOf(mode));
                rule.setAction(updateAction(existingAction, bool(entry, "remind")));
                rules.add(rule);
            }
            // 保留已有规则的执行顺序，新增关键词追加到末尾。
            List<ViolationRule> ordered = new ArrayList<>();
            Set<ViolationRule> retained = Collections.newSetFromMap(new IdentityHashMap<>());
            retained.addAll(rules);
            Set<ViolationRule> original = Collections.newSetFromMap(new IdentityHashMap<>());
            original.addAll(previous);
            for (ViolationRule rule : previous) if (rule != null
                    && (rule.getType() != ViolationRuleType.KEYWORD || retained.contains(rule))) ordered.add(rule);
            for (ViolationRule rule : rules) if (!original.contains(rule)) ordered.add(rule);
            keywords.setRules(ordered);
            keywords.setEnabled(bool(request, "keywordEnabled"));
            if (current == null || current.getKeywordRecall() == null)
                keywords.setAction(action(true));
            var ai = next.getAiRecall();
            ai.setEnabled(bool(request, "aiEnabled"));
            if (request.has("aiSchedule")) ai.setSchedule(parseSchedule(request.get("aiSchedule")));
            if (request.has("aiType")) {
                JsonNode type = request.path("aiType");
                if (!type.isIntegralNumber() || !type.canConvertToInt() || type.intValue() < 0 || type.intValue() > 3) throw invalid();
                ai.setType(type.intValue());
            }
            boolean existingAi = current != null && current.getAiRecall() != null
                    && !current.getAiRecall().equals(new AiModerationConfig());
            ai.setAction(updateAction(existingAi ? current.getAiRecall().getAction() : null, bool(request, "aiRemind")));
            if (!existingAi) {
                ai.setCustomOutput("");
                ai.setUseCustomOutputAsReminder(false);
            }
            List<String> domains = new ArrayList<>();
            used.clear();
            for (JsonNode entry : array(request, "domains")) {
                fields(entry, Set.of("id", "value"));
                String value;
                if (entry.has("id")) value = ai.getAllowedDomains().get(index(entry, ai.getAllowedDomains().size(), used));
                else {
                    value = string(entry, "value", 512, false);
                    try { Pattern.compile(value); } catch (PatternSyntaxException invalid) { throw new Problem(400, "INVALID_URL_PATTERN"); }
                }
                if (".*".equals(value) || value == null) throw invalid();
                if (!domains.contains(value)) domains.add(value);
            }
            if (bool(request, "allowAllLinks")) domains.addFirst(".*");
            ai.setAllowedDomains(domains);
            if (request.has("customPrompt")) ai.setSystemPrompt(string(request, "customPrompt", 8000, true));
            return next;
        });
        checkSession.run();
        return view(identity, groupId, saved);
    }

    private void authorize(MiniappSessions.Identity identity, String groupId) throws SQLException {
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,256}")) throw invalid();
        if (!canManage(identity)) throw new Problem(403, "MODERATION_FORBIDDEN");
        var group = bindings.detail(identity.userId(), groupId);
        if (group == null) throw new Problem(403, "MODERATION_FORBIDDEN");
        if (!group.available() || group.restricted()) throw new Problem(403, "MODERATION_FORBIDDEN");
        // QQ 成员查询接口尚未开放，当前使用已通过群主消息事件验证的绑定授权。
        // 接口开放后可恢复以下实时身份校验。
        // var member = members.apply(groupId, identity.userId());
        // if (member == null) throw new Problem(502, "OWNER_CHECK_UNAVAILABLE");
        // if (member.bot() || !"owner".equalsIgnoreCase(member.memberRole())
        //         || !(identity.userId().equals(member.memberOpenId()) || identity.userId().equals(member.unionOpenId())))
        //     throw new Problem(403, "MODERATION_FORBIDDEN");
    }

    private View view(MiniappSessions.Identity identity, String groupId, GroupModerationSettings stored) {
        if (!canManage(identity)) throw new Problem(403, "MODERATION_FORBIDDEN");
        var settings = defaults(stored);
        var config = settings.getKeywordRecall();
        List<Keyword> keywords = new ArrayList<>();
        List<ReadOnlyRule> otherRules = new ArrayList<>();
        for (int i = 0; i < config.getRules().size(); i++) {
            var rule = config.getRules().get(i);
            if (rule != null && rule.getType() == ViolationRuleType.KEYWORD)
                keywords.add(new Keyword(Integer.toString(i), mask(rule.getKeyword(), false),
                        rule.getMatchMode() == MatchMode.EQUALS ? MatchMode.EQUALS : MatchMode.CONTAINS,
                        effectiveAction(rule, config).isRemind(), ruleDetails(rule, config)));
            else if (rule != null) {
                List<ReadOnlyField> details = new ArrayList<>();
                details.add(new ReadOnlyField("状态", config.isEnabled() ? "已启用" : "已关闭"));
                if (effectiveAction(rule, config).isRecall()) details.add(new ReadOnlyField("撤回消息", "开启"));
                details.add(new ReadOnlyField("违规提醒", effectiveAction(rule, config).isRemind() ? "开启" : "关闭"));
                details.addAll(ruleDetails(rule, config));
                otherRules.add(new ReadOnlyRule(rule.getType() == ViolationRuleType.LINK ? "链接规则" : "小程序规则", details));
            }
        }
        var ai = settings.getAiRecall();
        List<Domain> domains = new ArrayList<>();
        for (int i = 0; i < ai.getAllowedDomains().size(); i++) {
            String value = ai.getAllowedDomains().get(i);
            if (value != null && !".*".equals(value)) domains.add(new Domain(Integer.toString(i), mask(value, true)));
        }
        boolean canPrompt = promptPermission.test(identity.userId());
        return new View(true, canPrompt, revision(groupId, stored), config.isEnabled(), keywords,
                ai.isEnabled(), ai.getType(), ai.getAction().isRemind(), ai.getAllowedDomains().contains(".*"), domains,
                canPrompt && ai.getSystemPrompt() != null && !ai.getSystemPrompt().isBlank(),
                stored != null && stored.getAiRecall() != null ? aiDetails(ai) : List.of(), otherRules,
                canPrompt ? Objects.toString(ai.getSystemPrompt(), "") : null,
                ai.getSchedule() == null ? new AiModerationSchedule() : ai.getSchedule());
    }

    private static List<ReadOnlyField> ruleDetails(ViolationRule rule, KeywordModerationConfig config) {
        List<ReadOnlyField> fields = new ArrayList<>();
        addText(fields, "备注", rule.getRemark());
        fields.addAll(actionDetails(effectiveAction(rule, config)));
        return fields;
    }

    private static List<ReadOnlyField> actionDetails(ModerationAction action) {
        List<ReadOnlyField> fields = new ArrayList<>();
        if (!action.isRecall()) fields.add(new ReadOnlyField("撤回消息", "关闭"));
        if (action.isMute()) fields.add(new ReadOnlyField("禁言", action.getMuteSeconds() + " 秒"));
        if (!REMINDER.equals(action.getRemindMessage())) addText(fields, "固定提醒内容", action.getRemindMessage());
        return fields;
    }

    private static List<ReadOnlyField> aiDetails(AiModerationConfig ai) {
        if (ai.equals(new AiModerationConfig())) return List.of();
        List<ReadOnlyField> fields = new ArrayList<>();
        addText(fields, "自定义输出", ai.getCustomOutput());
        if (ai.isUseCustomOutputAsReminder()) fields.add(new ReadOnlyField("提醒来源", "优先使用接口自定义输出，未返回时使用固定提醒"));
        fields.addAll(actionDetails(ai.getAction()));
        return fields;
    }

    private static AiModerationSchedule parseSchedule(JsonNode node) {
        fields(node, Set.of("enabled", "startDate", "endDate", "startTime", "endTime", "daysOfWeek"));
        var schedule = new AiModerationSchedule();
        schedule.setEnabled(bool(node, "enabled"));
        String startDate = string(node, "startDate", 10, true);
        String endDate = string(node, "endDate", 10, true);
        String startTime = string(node, "startTime", 5, false);
        String endTime = string(node, "endTime", 5, false);
        try {
            for (String date : List.of(startDate, endDate)) if (!date.isEmpty()) {
                if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
                LocalDate.parse(date);
            }
            if (!startDate.isEmpty() && !endDate.isEmpty() && LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate))) throw invalid();
            for (String time : List.of(startTime, endTime)) {
                if (!time.matches("[0-9]{2}:[0-9]{2}")) throw invalid();
                LocalTime.parse(time);
            }
        } catch (DateTimeException invalidDate) { throw invalid(); }
        List<Integer> days = new ArrayList<>();
        for (JsonNode day : array(node, "daysOfWeek")) {
            if (!day.isIntegralNumber() || !day.canConvertToInt() || day.intValue() < 1 || day.intValue() > 7 || days.contains(day.intValue())) throw invalid();
            days.add(day.intValue());
        }
        days.sort(Integer::compareTo);
        schedule.setStartDate(startDate); schedule.setEndDate(endDate);
        schedule.setStartTime(startTime); schedule.setEndTime(endTime);
        schedule.setDaysOfWeek(days);
        return schedule;
    }

    private static void addText(List<ReadOnlyField> fields, String label, String value) {
        if (value != null && !value.isBlank()) fields.add(new ReadOnlyField(label, value));
    }

    private static GroupModerationSettings defaults(GroupModerationSettings stored) {
        var next = stored == null ? new GroupModerationSettings() : JSON.convertValue(stored, GroupModerationSettings.class);
        if (next.getKeywordRecall() == null) next.setKeywordRecall(new KeywordModerationConfig());
        if (next.getKeywordRecall().getRules() == null) next.getKeywordRecall().setRules(new ArrayList<>());
        if (next.getAiRecall() == null) next.setAiRecall(new AiModerationConfig());
        if (next.getAiRecall().getAction() == null) next.getAiRecall().setAction(new ModerationAction());
        if (next.getAiRecall().getAllowedDomains() == null) next.getAiRecall().setAllowedDomains(new ArrayList<>());
        if (stored == null) next.getAiRecall().setAllowedDomains(new ArrayList<>(List.of(".*")));
        return next;
    }

    private String revision(String groupId, GroupModerationSettings settings) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(revisionKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(JSON.writeValueAsBytes(List.of(groupId, settings == null ? "absent" : settings))));
        } catch (Exception failure) { throw new IllegalStateException("Unable to calculate moderation revision", failure); }
    }

    static String mask(String value, boolean keepLast) {
        int[] points = Objects.toString(value, "").codePoints().toArray();
        if (points.length == 0) return "";
        String first = new String(points, 0, 1);
        if (!keepLast || points.length < 2) return first + "*".repeat(points.length - 1);
        return first + "*".repeat(points.length - 2) + new String(points, points.length - 1, 1);
    }

    private static ModerationAction effectiveAction(ViolationRule rule, KeywordModerationConfig config) {
        return rule.getAction() != null ? rule.getAction() : config.getAction() != null ? config.getAction() : new ModerationAction();
    }

    private static ModerationAction action(boolean remind) {
        var action = new ModerationAction();
        action.setRemind(remind);
        action.setRemindMessage(REMINDER);
        action.setNotifyDebugGroup(true);
        return action;
    }

    private static ModerationAction updateAction(ModerationAction existing, boolean remind) {
        if (existing == null) return action(remind);
        ModerationAction next = JSON.convertValue(existing, ModerationAction.class);
        next.setRemind(remind);
        return next;
    }

    private static int index(JsonNode entry, int size, Set<String> used) {
        if (entry.has("value")) throw invalid();
        String id = string(entry, "id", 10, false);
        if (!id.matches("0|[1-9][0-9]{0,8}") || !used.add(id)) throw invalid();
        int index = Integer.parseInt(id);
        if (index >= size) throw invalid();
        return index;
    }

    private static JsonNode array(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isArray() || value.size() > 100) throw invalid();
        return value;
    }

    private static String string(JsonNode node, String field, int max, boolean empty) {
        var value = node.path(field);
        if (!value.isTextual() || value.textValue().length() > max || !empty && value.textValue().isBlank()) throw invalid();
        return value.textValue();
    }

    private static boolean bool(JsonNode node, String field) {
        if (!node.path(field).isBoolean()) throw invalid();
        return node.path(field).booleanValue();
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid();
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw invalid(); });
    }

    private static Problem invalid() { return new Problem(400, "INVALID_MODERATION_SETTINGS"); }
}
