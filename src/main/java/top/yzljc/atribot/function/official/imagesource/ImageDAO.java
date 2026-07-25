package top.yzljc.atribot.function.official.imagesource;

/**
 * 对外取图用的轻量对象。
 *
 * @param url 可直接访问的图片地址
 * @param w   图片宽度
 * @param h   图片高度
 */
public record ImageDAO(String url, int w, int h) {
}
