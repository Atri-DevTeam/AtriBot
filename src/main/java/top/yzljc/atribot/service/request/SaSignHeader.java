package top.yzljc.atribot.service.request;

import cn.dev33.satoken.sign.template.SaSignUtil;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SaSignHeader
 * @Created_at 2026/05/12
 * @Project AtriBot
 * @Package top.yzljc.qqbot.service.request
 */
public class SaSignHeader {

    public static String sign(String url) {

        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = Long.toHexString(System.nanoTime());

        Map<String, Object> signKey = Map.of(
                "timestamp", timestamp,
                "nonce", nonce
        );

        String sign = SaSignUtil.createSign(signKey);

        String separator = url.contains("?") ? "&" : "?";

        return url + separator + "timestamp=" + timestamp + "&nonce=" + nonce + "&sign=" + sign;
    }
}