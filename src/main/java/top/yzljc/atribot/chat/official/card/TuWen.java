package top.yzljc.atribot.chat.official.card;

import lombok.Getter;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName TuWen
 * @Created_at 2026/09/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
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
    private final String picUrl;
    private final String jumpUrl;

    /**
     * @param title 表示卡片消息的标题.
     * @param description 表示卡片消息的描述.
     * @param picUrl: 表示卡片消息中出现的图片.
     * @param jumpUrl: 表示卡片消息中的跳转链接.
     */
    public TuWen(String title, String description, String picUrl, String jumpUrl) {
        this.title = title;
        this.description = description;
        this.picUrl = picUrl;
        this.jumpUrl = jumpUrl;
    }

    @Override
    public Card.Type getType() {
        return Card.Type.TuWen;
    }

    @Override
    public Object toObject() {
        return Map.of("title", title, "description", description, "pic_url", picUrl, "url", jumpUrl);
    }
}
