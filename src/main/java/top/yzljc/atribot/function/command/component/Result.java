package top.yzljc.atribot.function.command.component;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.function.impl.ImageDTO;

/**
 * @param success 是否成功
 * @param message ugc 端生生成的响应文本
 * @param d       ugc 端生生成的响应数据
 * @param i       图片数据
 * @Author YZ_Ljc_
 */
public record Result(boolean success, String message, JsonNode d, ImageDTO i) {
}