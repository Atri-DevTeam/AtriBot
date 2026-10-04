package top.yzljc.atribot.command.impl;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.chat.kook.KookCard;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.command.KookCommandSender;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.kook.KookApiClient;
import top.yzljc.atribot.platform.kook.KookMessage;
import top.yzljc.atribot.platform.kook.KookUser;

import java.nio.file.Path;
import java.util.Base64;

/**
 * @Author YZ_Ljc_
 * @ClassName KookSenderImpl
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command.impl
 */
@RequiredArgsConstructor
public final class KookSenderImpl implements KookCommandSender {
    private final KookUser user;
    @Getter private final KookMessage message;
    private final KookApiClient api;

    @Override public Platform getPlatform() { return user.getPlatform(); }
    @Override public String getUserId() { return user.getUserId(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean hasPermission() { return user.hasPermission(); }
    @Override public boolean hasPermission(String permission) { return user.hasPermission(permission); }
    @Override public String getGuildId() { return message.getGuildId(); }
    @Override public String getChannelId() {
        return getPlatform() == Platform.KOOK_CHANNEL ? message.getTargetId() : null;
    }

    @Override
    public String sendMessage(String text) {
        return send(1, text);
    }

    @Override
    public String sendKMarkdown(String text) {
        return send(9, text);
    }

    @Override
    public String sendCard(KookCard... cards) {
        return send(10, KookCard.serialize(cards));
    }

    @Override
    public String sendMessage(ImageComponent image) {
        if (image == null || image.getType() == null
                || image.getData() == null || image.getData().isBlank()) return null;
        String url = image.getData().trim();
        if (image.getType() == ImageType.BASE64) {
            String encoded = url;
            if (encoded.startsWith("base64://")) encoded = encoded.substring("base64://".length());
            else if (encoded.startsWith("data:image/")) {
                int separator = encoded.indexOf(";base64,");
                if (separator < 0) return null;
                encoded = encoded.substring(separator + ";base64,".length());
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
                return null;
            }
            url = api.uploadAsset(bytes, "image");
        } else {
            url = api.uploadAsset(url);
        }
        if (url == null) return null;
        if (image.getText() != null && !image.getText().isBlank()
                && sendMessage(image.getText()) == null) return null;
        return send(2, url);
    }

    @Override
    public String sendFile(Path file) {
        String url = api.uploadAsset(file);
        return url == null ? null : send(4, url);
    }

    @Override
    public boolean recall() {
        return message.recall();
    }

    @Override
    public boolean recall(String messageId) {
        return api.recallMessage(getPlatform(), messageId);
    }

    private String send(int type, String content) {
        String target = getPlatform() == Platform.KOOK_CHANNEL ? message.getTargetId() : user.getUserId();
        return api.sendMessage(getPlatform(), target, type, content, message.getMessageId());
    }
}
