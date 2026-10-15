package top.yzljc.atricore.chat;

import lombok.Getter;

/**
 * 图片来源及附带文本，具体支持的数据类型由发送平台决定
 *
 * @Author YZ_Ljc_
 * @ClassName ImageComponent
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat
 */
@Getter
public class ImageComponent {

    private String data;
    private String text;
    private ImageType type;

    /**
     * 创建使用 URL 的图片组件
     *
     * @param data 图片 URL
     */
    public ImageComponent(String data) {
        this.data = data;
        this.type = ImageType.URL;
    }

    /**
     * 创建指定来源类型的图片组件
     *
     * @param data 图片 URL 或 Base64 数据
     * @param type 数据类型
     */
    public ImageComponent(String data, ImageType type) {
        this.data = data;
        this.type = type;
    }

    /**
     * 创建使用 URL 的图片组件
     *
     * @param url 图片 URL
     * @return 图片组件
     */
    public static ImageComponent imageOf(String url) {
        return new ImageComponent(url);
    }

    /**
     * 创建指定来源类型的图片组件
     *
     * @param data 图片 URL 或 Base64 数据
     * @param type 数据类型
     * @return 图片组件
     */
    public static ImageComponent imageOf(String data, ImageType type) {
        return new ImageComponent(data, type);
    }

    public ImageComponent setData(String data) {
        this.data = data;
        return this;
    }

    public ImageComponent setText(String text) {
        this.text = text;
        return this;
    }

    public ImageComponent setType(ImageType type) {
        this.type = type;
        return this;
    }
}
