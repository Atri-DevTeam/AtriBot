package top.yzljc.atricore.chat.official.ark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ark 模板的键值及列表条目
 *
 * @Author YZ_Ljc_
 * @ClassName ArkValue
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.ark
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArkValue(String key, String value, List<Item> obj) {
    public ArkValue(String key, String value) {
        this(key, value, null);
    }

    /**
     * Ark 列表中的单个条目
     */
    public record Item(@JsonProperty("obj_kv") List<ArkValue> values) {
    }
}
