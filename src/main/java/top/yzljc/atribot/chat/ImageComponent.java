package top.yzljc.atribot.chat;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageComponent
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat
 */
@Getter
public class ImageComponent {

    private String data;
    private String text;
    private ImageType type;

    /** 默认情况下，选择分类为{@code ImageType.URL} */
    public ImageComponent(String data) {
        this.data = data;
        this.type = ImageType.URL;
    }

    public ImageComponent(String data, ImageType type) {
        this.data = data;
        this.type = type;
    }

    public static ImageComponent imageOf(String url) {
        return new ImageComponent(url);
    }

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
