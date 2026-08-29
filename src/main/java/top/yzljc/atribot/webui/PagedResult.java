package top.yzljc.atribot.webui;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName PagedResult
 * @Created_at 2026/08/29
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 * @Description 分页列表的统一响应体，JSON 结构固定为 {items, total, page, pageSize}
 */
public record PagedResult<T>(List<T> items, int total, int page, int pageSize) {
}
