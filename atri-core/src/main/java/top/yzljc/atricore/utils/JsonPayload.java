package top.yzljc.atricore.utils;

/**
 * @Author YZ_Ljc_
 * @ClassName JsonPayload
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.utils
 */
public interface JsonPayload {

    /**
     * 转换为可由 JSON 序列化器处理的数据结构
     *
     * @return 结构化对象或 Map，不是预先编码的 JSON 字符串
     */
    Object toObject();
}
