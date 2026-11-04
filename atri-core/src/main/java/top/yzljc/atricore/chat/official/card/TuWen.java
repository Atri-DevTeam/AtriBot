package top.yzljc.atricore.chat.official.card;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName TuWen
 * @Created_at 2026/09/28
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.card
 * @Description 当type为tuwen时 会发送一个包括标题,描述,图片,跳转链接的消息.
 */
@Getter
public class TuWen implements Card.CardContent {

//            "content": {
//                "description": "2分钟完成注册并创建QQBot 无缝对接OpenClaw",
//                "pic_url": "https://qqminiapp.cdn-go.cn/qq-open-platform/9b9327f1/assets/33-2-GiI9drV8.png",
//                "title": "QQ开放平台",
//                "url": "https://q.qq.com/#/"
//    }
//},

    private final String title;
    private final String description;
    @JsonProperty("pic_url")
    private final String picUrl;
    @JsonProperty("url")
    private final String jumpUrl;

    /**
     * @param title 表示卡片消息的标题.
     * @param description 表示卡片消息的描述.
     * @param picUrl 表示卡片消息中出现的图片.
     * @param jumpUrl 表示卡片消息中的跳转链接.
     */
    public TuWen(String title, String description, String picUrl, String jumpUrl) {
        this.title = title;
        this.description = description;
        this.picUrl = picUrl;
        this.jumpUrl = jumpUrl;
    }

    @Override
    @JsonIgnore
    public Card.Type getType() {
        return Card.Type.TuWen;
    }

    /** 返回图文卡片内容的协议对象 */
    @Override
    @JsonValue
    public Payload toPayload() {
        return new Payload(title, description, picUrl, jumpUrl);
    }

    /** 图文卡片的标题、说明、图片和跳转地址 */
    public record Payload(String title, String description, @JsonProperty("pic_url") String picUrl,
                          @JsonProperty("url") String jumpUrl) {}

    @Override
    public Object toObject() {
        return Map.of("title", title, "description", description, "pic_url", picUrl, "url", jumpUrl);
    }
}
