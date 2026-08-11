package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.chat.official.management.JoinApprovalStrategy;
import top.yzljc.atribot.chat.official.management.JoinRequestApproval;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.repo.JoinApprovalSnapshotRepo;
import top.yzljc.atribot.webui.repo.JoinApprovalWhitelistRepo;

import java.util.ArrayList;
import java.util.List;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;
import static top.yzljc.atribot.webui.WebUiSupport.stringList;

/** 入群审批：策略管理 + 待审批列表 */
public class JoinApprovalController {

    /**
     * 拉取全部入群审批策略，优先读本地快照
     * 快照为空时才调官方接口并回填快照
     */
    public static void listApprovalStrategies(Context ctx) {
        List<JoinApprovalStrategy.StrategyData> snapshot = JoinApprovalSnapshotRepo.getSnapshot();
        if (!snapshot.isEmpty()) {
            ctx.json(Result.success(snapshot));
            return;
        }
        List<JoinApprovalStrategy.StrategyData> list = pullStrategiesFromOfficial();
        if (list == null) {
            ctx.json(Result.fail(500, "拉取入群审批策略失败"));
            return;
        }
        JoinApprovalSnapshotRepo.saveSnapshot(list);
        ctx.json(Result.success(list));
    }

    /** 强制刷新：重新拉取官方全量策略并更新本地快照 */
    public static void refreshApprovalStrategies(Context ctx) {
        List<JoinApprovalStrategy.StrategyData> list = pullStrategiesFromOfficial();
        if (list == null) {
            ctx.json(Result.fail(500, "刷新入群审批策略失败"));
            return;
        }
        JoinApprovalSnapshotRepo.saveSnapshot(list);
        ctx.json(Result.success(list));
    }

    /** 创建入群审批策略，body: {groupOpenIds:[], enable, expireAt?, remark?}，返回新策略 ID */
    public static void createApprovalStrategy(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        List<String> groupOpenIds = stringList(body, "groupOpenIds");
        boolean enable = body != null && body.path("enable").asBoolean(true);
        String expireAt = body != null ? body.path("expireAt").asText(null) : null;
        String remark = body != null ? body.path("remark").asText(null) : null;
        if (groupOpenIds.isEmpty()) {
            ctx.json(Result.fail(400, "至少选择一个关联群"));
            return;
        }
        String strategyId = JoinApprovalStrategy.createStrategy(groupOpenIds, null, enable, expireAt, remark);
        if (strategyId == null) {
            ctx.json(Result.fail(500, "创建入群审批策略失败"));
            return;
        }
        refreshApprovalSnapshotQuietly();
        ctx.json(Result.success(strategyId));
    }

    /** 修改入群审批策略属性，body: {enable?, expireAt?, remark?}，只传需要改的字段 */
    public static void updateApprovalStrategy(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        if (isBlank(strategyId)) {
            ctx.json(Result.fail(400, "strategyId 不能为空"));
            return;
        }
        Boolean enable = body != null && body.has("enable") ? body.path("enable").asBoolean() : null;
        String expireAt = body != null ? body.path("expireAt").asText(null) : null;
        String remark = body != null ? body.path("remark").asText(null) : null;
        boolean ok = JoinApprovalStrategy.updateStrategy(strategyId, enable, expireAt, remark);
        if (!ok) {
            ctx.json(Result.fail(500, "修改入群审批策略失败"));
            return;
        }
        refreshApprovalSnapshotQuietly();
        ctx.json(Result.success(null));
    }

    /** 修改入群审批策略关联群，body: {op:"add"|"del", groupOpenIds:[]} */
    public static void updateApprovalStrategyGroups(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String op = body != null ? body.path("op").asText(null) : null;
        List<String> groupOpenIds = stringList(body, "groupOpenIds");
        if (isBlank(strategyId) || isBlank(op) || groupOpenIds.isEmpty()) {
            ctx.json(Result.fail(400, "strategyId、op、groupOpenIds 不能为空"));
            return;
        }
        JoinApprovalStrategy.GroupActionOp actionOp = "del".equalsIgnoreCase(op)
                ? JoinApprovalStrategy.GroupActionOp.DEL
                : JoinApprovalStrategy.GroupActionOp.ADD;
        boolean ok = JoinApprovalStrategy.updateStrategyGroups(strategyId, actionOp, groupOpenIds, null);
        if (!ok) {
            ctx.json(Result.fail(500, "修改入群审批策略关联群失败"));
            return;
        }
        refreshApprovalSnapshotQuietly();
        ctx.json(Result.success(null));
    }

    /** 删除入群审批策略 */
    public static void deleteApprovalStrategy(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        if (isBlank(strategyId)) {
            ctx.json(Result.fail(400, "strategyId 不能为空"));
            return;
        }
        boolean ok = JoinApprovalStrategy.deleteStrategy(strategyId);
        if (!ok) {
            ctx.json(Result.fail(500, "删除入群审批策略失败"));
            return;
        }
        refreshApprovalSnapshotQuietly();
        JoinApprovalWhitelistRepo.clearStrategy(strategyId);
        ctx.json(Result.success(null));
    }

    /** 手动执行入群审批策略全量扫描（官方异步执行，约 10 分钟完成） */
    public static void executeApprovalStrategy(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        if (isBlank(strategyId)) {
            ctx.json(Result.fail(400, "strategyId 不能为空"));
            return;
        }
        boolean ok = JoinApprovalStrategy.executeStrategy(strategyId);
        if (!ok) {
            ctx.json(Result.fail(500, "执行入群审批策略失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    /** 修改入群审批策略白名单，body: {op:"add"|"del", users:[]} */
    public static void updateApprovalWhitelist(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String op = body != null ? body.path("op").asText(null) : null;
        List<String> users = stringList(body, "users");
        if (isBlank(strategyId) || isBlank(op) || users.isEmpty()) {
            ctx.json(Result.fail(400, "strategyId、op、users 不能为空"));
            return;
        }
        JoinApprovalStrategy.WhitelistOp whitelistOp = "del".equalsIgnoreCase(op)
                ? JoinApprovalStrategy.WhitelistOp.DEL
                : JoinApprovalStrategy.WhitelistOp.ADD;
        boolean ok = JoinApprovalStrategy.updateWhitelist(strategyId, whitelistOp, users);
        if (!ok) {
            ctx.json(Result.fail(500, "修改入群审批白名单失败"));
            return;
        }
        // 同步官方成功后，镜像到本地记录，供页面展示已有号码
        if (whitelistOp == JoinApprovalStrategy.WhitelistOp.ADD) {
            JoinApprovalWhitelistRepo.addUsers(strategyId, users);
        } else {
            JoinApprovalWhitelistRepo.removeUsers(strategyId, users);
        }
        ctx.json(Result.success(null));
    }

    /** 查询策略本地白名单号码列表（官方只返回数量不返回明细，号码由页面填写时本地镜像） */
    public static void listApprovalWhitelist(Context ctx) {
        String strategyId = ctx.pathParam("strategyId");
        if (isBlank(strategyId)) {
            ctx.json(Result.fail(400, "strategyId 不能为空"));
            return;
        }
        ctx.json(Result.success(JoinApprovalWhitelistRepo.list(strategyId)));
    }

    /** 私有：调官方接口分页拉取全部入群审批策略，失败返回 null */
    private static List<JoinApprovalStrategy.StrategyData> pullStrategiesFromOfficial() {
        List<JoinApprovalStrategy.StrategyData> all = new ArrayList<>();
        String cursor = null;
        while (true) {
            JoinApprovalStrategy.StrategyListResult page = JoinApprovalStrategy.listStrategies(cursor, 100);
            if (page == null) {
                return null;
            }
            all.addAll(page.strategies());
            if (page.nextCursor() == null || page.nextCursor().isBlank()) {
                break;
            }
            cursor = page.nextCursor();
        }
        return all;
    }

    /** 私有：拉官方全量更新快照，失败静默保留旧快照（供创建/修改/删除成功后刷新） */
    private static void refreshApprovalSnapshotQuietly() {
        List<JoinApprovalStrategy.StrategyData> list = pullStrategiesFromOfficial();
        if (list != null) {
            JoinApprovalSnapshotRepo.saveSnapshot(list);
        }
    }

    /** 拉取入群申请列表，支持 cursor/limit 分页 */
    public static void listJoinRequests(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String cursor = ctx.queryParam("cursor");
        int limit = parseInt(ctx.queryParam("limit"), 20);
        if (isBlank(groupOpenId)) {
            ctx.json(Result.fail(400, "groupOpenId 不能为空"));
            return;
        }
        var result = JoinRequestApproval.getJoinRequestList(groupOpenId, cursor, limit);
        if (result == null) {
            ctx.json(Result.fail(500, "拉取入群申请列表失败"));
            return;
        }
        ctx.json(Result.success(result));
    }

    /** 通过入群申请，body: {joinRequestId} */
    public static void approveJoinRequest(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String memberOpenId = ctx.pathParam("memberOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String joinRequestId = body != null ? body.path("joinRequestId").asText(null) : null;
        if (isBlank(groupOpenId) || isBlank(memberOpenId) || isBlank(joinRequestId)) {
            ctx.json(Result.fail(400, "groupOpenId、memberOpenId、joinRequestId 不能为空"));
            return;
        }
        boolean ok = JoinRequestApproval.approveJoinRequest(groupOpenId, memberOpenId, joinRequestId);
        if (!ok) {
            ctx.json(Result.fail(500, "通过入群申请失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    /** 拒绝入群申请，body: {joinRequestId, rejectReason?, addToMemberBlacklist?} */
    public static void declineJoinRequest(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        String memberOpenId = ctx.pathParam("memberOpenId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String joinRequestId = body != null ? body.path("joinRequestId").asText(null) : null;
        String rejectReason = body != null ? body.path("rejectReason").asText(null) : null;
        boolean addToMemberBlacklist = body != null && body.path("addToMemberBlacklist").asBoolean(false);
        if (isBlank(groupOpenId) || isBlank(memberOpenId) || isBlank(joinRequestId)) {
            ctx.json(Result.fail(400, "groupOpenId、memberOpenId、joinRequestId 不能为空"));
            return;
        }
        boolean ok = JoinRequestApproval.declineJoinRequest(groupOpenId, memberOpenId, joinRequestId, rejectReason, addToMemberBlacklist);
        if (!ok) {
            ctx.json(Result.fail(500, "拒绝入群申请失败"));
            return;
        }
        ctx.json(Result.success(null));
    }
}
