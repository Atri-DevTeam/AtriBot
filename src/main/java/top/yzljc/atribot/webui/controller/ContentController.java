package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import lombok.Data;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.*;
import top.yzljc.atribot.function.impl.ImageReviewStatus;
import top.yzljc.atribot.function.official.imagesource.ImageReviewService;
import top.yzljc.atribot.function.official.imagesource.ImageSourceClient;
import top.yzljc.atribot.function.official.loot.LootAdminClient;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/** 图源管理 + 抽卡系统 */
public class ContentController {

    // ============ 图源管理 ============

    private static final DateTimeFormatter GALLERY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void listGallery(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 24));
        String status = ctx.queryParam("status"); // PENDING | REVIEWED | DENIED | all

        String filter = ImageReviewStatus.isValid(status) ? status.toUpperCase() : null;
        int total = ImageSourceRepository.countByStatus(filter);
        List<ImageSourceDTO> list = ImageSourceRepository.findPaginated(filter, page, pageSize);

        List<GalleryItemDTO> items = list.stream().map(ContentController::toGalleryItem).toList();
        ctx.json(Result.success(new GalleryListResult(items, total, page, pageSize)));
    }

    public static void countGallery(Context ctx) {
        int pending = ImageSourceRepository.countByStatus(ImageReviewStatus.PENDING.name());
        int reviewed = ImageSourceRepository.countByStatus(ImageReviewStatus.REVIEWED.name());
        int denied = ImageSourceRepository.countByStatus(ImageReviewStatus.DENIED.name());
        int all = ImageSourceRepository.countByStatus(null);
        ctx.json(Result.success(new GalleryCountDTO(pending, reviewed, denied, all)));
    }

    public static void reviewGallery(Context ctx) {
        ReviewGalleryDTO dto = ctx.bodyAsClass(ReviewGalleryDTO.class);
        if (isBlank(dto.getId()) || !ImageReviewStatus.isValid(dto.getStatus())) {
            ctx.json(Result.fail(400, "id 与合法的 status 不能为空"));
            return;
        }
        boolean success = ImageReviewService.review(dto.getId(), ImageReviewStatus.of(dto.getStatus()),
                REVIEWER_NAME, dto.getRemark());
        ctx.json(success ? Result.success("ok") : Result.fail(500, "审核失败，可能该投稿不存在"));
    }

    public static void reviewGalleryBatch(Context ctx) {
        ReviewGalleryBatchDTO dto = ctx.bodyAsClass(ReviewGalleryBatchDTO.class);
        if (dto.getIds() == null || dto.getIds().isEmpty() || !ImageReviewStatus.isValid(dto.getStatus())) {
            ctx.json(Result.fail(400, "ids 与合法的 status 不能为空"));
            return;
        }
        ImageReviewStatus status = ImageReviewStatus.of(dto.getStatus());
        int ok = 0;
        for (String id : dto.getIds()) {
            if (isBlank(id)) continue;
            if (ImageReviewService.review(id, status, REVIEWER_NAME, dto.getRemark())) ok++;
        }
        ctx.json(Result.success(new GalleryBatchResult(ok, dto.getIds().size())));
    }

    public static void deleteGallery(Context ctx) {
        DeleteGalleryDTO dto = ctx.bodyAsClass(DeleteGalleryDTO.class);
        if (isBlank(dto.getId())) {
            ctx.json(Result.fail(400, "id 不能为空"));
            return;
        }
        ImageSourceDTO image = ImageSourceRepository.findById(dto.getId());
        if (image == null) {
            ctx.json(Result.fail(404, "投稿记录不存在"));
            return;
        }
        ImageSourceClient.RemoteResult remoteResult = ImageSourceClient.delete(image);
        if (!remoteResult.ok()) {
            ctx.json(Result.fail(500, "远端删除失败：" + remoteResult.message()));
            return;
        }
        boolean success = ImageSourceRepository.delete(dto.getId());
        ctx.json(success ? Result.success("ok") : Result.fail(500, "删除失败，可能该投稿不存在"));
    }

    private static GalleryItemDTO toGalleryItem(ImageSourceDTO dto) {
        return new GalleryItemDTO(
                dto.getId(), dto.getImageUuid(), dto.getPlatform(), dto.getUploaderId(), dto.getUploaderName(),
                dto.getGroupId(), ImageSourceClient.viewUrl(dto), dto.getFileName(), dto.getContentType(),
                dto.getWidth(), dto.getHeight(), dto.getFileSize(),
                dto.getProcessedWidth(), dto.getProcessedHeight(), dto.getProcessedFileSize(),
                dto.getHash(), dto.getReviewStatus(),
                dto.getReviewer(), dto.getReviewRemark(),
                dto.getReviewTime() != null ? dto.getReviewTime().toLocalDateTime().format(GALLERY_TIME_FMT) : null,
                dto.getCreateTime() != null ? dto.getCreateTime().toLocalDateTime().format(GALLERY_TIME_FMT) : null,
                dto.isNotified()
        );
    }

    /** WebUI 目前只有单一管理员会话，没有独立账号体系，审核人统一记为 webui */
    private static final String REVIEWER_NAME = "webui";

    public record GalleryItemDTO(String id, String imageUuid, String platform, String uploaderId,
                                 String uploaderName, String groupId, String displayUrl, String fileName,
                                 String contentType, int width, int height, long fileSize,
                                 int processedWidth, int processedHeight, long processedFileSize, String hash,
                                 String reviewStatus, String reviewer, String reviewRemark,
                                 String reviewTime, String createTime,
                                 @JsonProperty("isNotified") boolean isNotified) {
    }

    public record GalleryListResult(List<GalleryItemDTO> items, int total, int page, int pageSize) {
    }

    public record GalleryCountDTO(int pending, int reviewed, int denied, int all) {
    }

    public record GalleryBatchResult(int success, int total) {
    }

    @Data
    public static class ReviewGalleryDTO {
        private String id;
        private String status;
        private String remark;
    }

    @Data
    public static class ReviewGalleryBatchDTO {
        private List<String> ids;
        private String status;
        private String remark;
    }

    @Data
    public static class DeleteGalleryDTO {
        private String id;
    }

    // ============ 抽卡系统管理 ============

    public static void listLootItems(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        JsonNode resp = LootAdminClient.listItems(page, pageSize);
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "抽卡目录服务暂不可用"));
            return;
        }
        JsonNode data = resp.path("data");
        if (data instanceof ObjectNode objectNode) {
            objectNode.put("imageBaseUrl", ResourcesProperties.LOOTS_ITEM_IMAGE_API);
        }
        ctx.json(Result.success(data));
    }

    public static void createLootItem(Context ctx) {
        String displayName = ctx.formParam("displayName");
        String description = ctx.formParam("description");
        UploadedFile file = ctx.uploadedFile("image");
        if (isBlank(displayName) || file == null) {
            ctx.json(Result.fail(400, "displayName 与 image 不能为空"));
            return;
        }

        byte[] bytes;
        try (InputStream is = file.content()) {
            bytes = is.readAllBytes();
        } catch (IOException e) {
            ctx.json(Result.fail(400, "读取上传图片失败"));
            return;
        }

        boolean special = "true".equalsIgnoreCase(ctx.formParam("special"));
        JsonNode resp = LootAdminClient.createItem(displayName, description, bytes, file.filename(), file.contentType(), special);
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "创建物品卡失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void updateLootItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        UpdateLootItemDTO dto = ctx.bodyAsClass(UpdateLootItemDTO.class);
        JsonNode resp = LootAdminClient.updateItem(itemId, dto.getDisplayName(), dto.getDescription());
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "更新物品卡失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void replaceLootItemImage(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        UploadedFile file = ctx.uploadedFile("image");
        if (file == null) {
            ctx.json(Result.fail(400, "image 不能为空"));
            return;
        }

        byte[] bytes;
        try (InputStream is = file.content()) {
            bytes = is.readAllBytes();
        } catch (IOException e) {
            ctx.json(Result.fail(400, "读取上传图片失败"));
            return;
        }

        JsonNode resp = LootAdminClient.replaceItemImage(itemId, bytes, file.filename(), file.contentType());
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "更换物品卡图片失败"));
            return;
        }
        ctx.json(Result.success(resp.path("data")));
    }

    public static void deleteLootItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        JsonNode resp = LootAdminClient.deleteItem(itemId);
        if (resp == null || resp.path("status").asInt() != 200) {
            ctx.json(Result.fail(502, "删除物品卡失败"));
            return;
        }
        ctx.json(Result.success("ok"));
    }

    public static void listCoinLeaderboard(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        String search = ctx.queryParam("search");
        List<LootRepository.CoinLeaderboardEntry> entries = LootRepository.getCoinLeaderboard(search, page, pageSize);
        int total = LootRepository.countUsersMatching(search);
        ctx.json(Result.success(new CoinLeaderboardResult(entries, total, page, pageSize)));
    }

    public static void adjustUserCoins(Context ctx) {
        String userId = ctx.pathParam("userId");
        AdjustCoinsDTO dto = ctx.bodyAsClass(AdjustCoinsDTO.class);
        if (isBlank(dto.getOp()) || dto.getAmount() == null) {
            ctx.json(Result.fail(400, "op 与 amount 不能为空"));
            return;
        }

        boolean success = switch (dto.getOp()) {
            case "set" -> LootRepository.setCoins(userId, dto.getAmount());
            case "add" -> LootRepository.addCoins(userId, dto.getAmount()) > 0;
            case "remove" -> LootRepository.removeCoins(userId, dto.getAmount());
            default -> false;
        };

        ctx.json(success ? Result.success(LootRepository.getCoins(userId)) : Result.fail(400, "操作失败，请检查 op 参数或余额"));
    }

    public static void listLootUsers(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = Math.min(100, parseInt(ctx.queryParam("pageSize"), 20));
        String search = ctx.queryParam("search");

        List<LootRepository.UserLootsSummary> summaries = LootRepository.listUsers(search, page, pageSize);
        int total = LootRepository.countUsersMatching(search);

        List<LootUserListItemDTO> items = summaries.stream()
                .map(s -> new LootUserListItemDTO(s.userId(), s.coins(), s.totalLootCount()))
                .toList();
        ctx.json(Result.success(new LootUserListResult(items, total, page, pageSize)));
    }

    public static void getUserLootsDetail(Context ctx) {
        String userId = ctx.pathParam("userId");
        LootRepository.UserLootsSummary summary = LootRepository.getUserSummary(userId);
        ctx.json(Result.success(new UserLootsDetailDTO(
                summary.userId(), summary.coins(), summary.loots(), ResourcesProperties.LOOTS_ITEM_IMAGE_API
        )));
    }

    public static void grantUserLoot(Context ctx) {
        String userId = ctx.pathParam("userId");
        GrantLootDTO dto = ctx.bodyAsClass(GrantLootDTO.class);
        if (isBlank(dto.getItemId()) || isBlank(dto.getDisplayName())) {
            ctx.json(Result.fail(400, "itemId 与 displayName 不能为空"));
            return;
        }
        String way = isBlank(dto.getWay()) ? "管理员赠与" : dto.getWay();
        LootRepository.LootRecord record = LootRepository.appendLoot(userId, dto.getItemId(), dto.getDisplayName(), way,
                Boolean.TRUE.equals(dto.getSpecial()));
        ctx.json(record != null ? Result.success(record) : Result.fail(500, "赠送物品失败"));
    }

    public static void revokeUserLoot(Context ctx) {
        String userId = ctx.pathParam("userId");
        String itemId = ctx.pathParam("itemId");
        boolean success = LootRepository.adminRemoveLoot(userId, itemId);
        ctx.json(success ? Result.success("ok") : Result.fail(404, "该用户未持有此物品卡"));
    }

    public static void revokeUserLootAll(Context ctx) {
        String userId = ctx.pathParam("userId");
        String itemId = ctx.pathParam("itemId");
        boolean success = LootRepository.adminRemoveAllLoot(userId, itemId);
        ctx.json(success ? Result.success("ok") : Result.fail(404, "该用户未持有此物品卡"));
    }

    public static void grantUserLootBatch(Context ctx) {
        String userId = ctx.pathParam("userId");
        GrantLootBatchDTO dto = ctx.bodyAsClass(GrantLootBatchDTO.class);
        List<GrantLootItemDTO> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            ctx.json(Result.fail(400, "items 不能为空"));
            return;
        }
        List<LootRepository.LootGrant> grants = items.stream()
                .filter(item -> item != null && !isBlank(item.getItemId()) && !isBlank(item.getDisplayName()))
                .map(item -> new LootRepository.LootGrant(item.getItemId(), item.getDisplayName()))
                .toList();
        if (grants.isEmpty()) {
            ctx.json(Result.fail(400, "至少需要一条有效物品卡"));
            return;
        }
        String way = isBlank(dto.getWay()) ? "管理员赠与" : dto.getWay();
        int success = LootRepository.appendLootsBatch(userId, grants, way, Boolean.TRUE.equals(dto.getSpecial()));
        ctx.json(Result.success(new LootBatchResult(success, grants.size())));
    }

    public static void revokeUserLootBatch(Context ctx) {
        String userId = ctx.pathParam("userId");
        ItemIdsDTO dto = ctx.bodyAsClass(ItemIdsDTO.class);
        Set<String> itemIds = normalizedItemIds(dto.getItemIds());
        if (itemIds.isEmpty()) {
            ctx.json(Result.fail(400, "itemIds 不能为空"));
            return;
        }
        int success = LootRepository.adminRemoveLootsBatch(userId, itemIds);
        ctx.json(Result.success(new LootBatchResult(success, itemIds.size())));
    }

    public static void setUserLootsSpecial(Context ctx) {
        String userId = ctx.pathParam("userId");
        SetLootSpecialDTO dto = ctx.bodyAsClass(SetLootSpecialDTO.class);
        Set<String> itemIds = normalizedItemIds(dto.getItemIds());
        if (itemIds.isEmpty() || dto.getSpecial() == null) {
            ctx.json(Result.fail(400, "itemIds 与 special 不能为空"));
            return;
        }
        int success = LootRepository.setLootsSpecial(userId, itemIds, dto.getSpecial());
        ctx.json(Result.success(new LootBatchResult(success, itemIds.size())));
    }

    private static Set<String> normalizedItemIds(List<String> itemIds) {
        Set<String> result = new LinkedHashSet<>();
        if (itemIds == null) return result;
        for (String itemId : itemIds) {
            if (!isBlank(itemId)) result.add(itemId.trim());
        }
        return result;
    }

    public record CoinLeaderboardResult(List<LootRepository.CoinLeaderboardEntry> items, int total, int page, int pageSize) {
    }

    public record LootUserListItemDTO(String userId, int coins, int cardCount) {
    }

    public record LootUserListResult(List<LootUserListItemDTO> items, int total, int page, int pageSize) {
    }

    public record UserLootsDetailDTO(String userId, int coins, List<LootRepository.LootRecord> loots,
                                     String imageBaseUrl) {
    }

    public record LootBatchResult(int success, int total) {
    }

    @Data
    public static class UpdateLootItemDTO {
        private String displayName;
        private String description;
    }

    @Data
    public static class AdjustCoinsDTO {
        private String op;
        private Integer amount;
    }

    @Data
    public static class GrantLootDTO {
        private String itemId;
        private String displayName;
        private String way;
        private Boolean special;
    }

    @Data
    public static class GrantLootBatchDTO {
        private List<GrantLootItemDTO> items;
        private String way;
        private Boolean special;
    }

    @Data
    public static class GrantLootItemDTO {
        private String itemId;
        private String displayName;
    }

    @Data
    public static class SetLootSpecialDTO {
        private List<String> itemIds;
        private Boolean special;
    }

    @Data
    public static class ItemIdsDTO {
        private List<String> itemIds;
    }
}
