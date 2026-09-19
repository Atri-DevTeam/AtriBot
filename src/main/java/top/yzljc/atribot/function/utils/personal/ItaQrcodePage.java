package top.yzljc.atribot.function.utils.personal;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ItaQrcodePage {
    private ItaQrcodePage() {}

    static String html() throws IOException {
        try (var input = ItaQrcodePage.class.getResourceAsStream("/ita-qrcode/group.html")) {
            if (input == null) throw new IOException("缺少 ITA 群二维码页面模板");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{QR_IMAGE}}", ItaQrcode.GROUP_IMAGE_PATH);
        }
    }

    /** 直接编码已保存的群地址，不访问目标链接，也不经过第三方二维码服务。 */
    static byte[] png(String target) throws IOException {
        try {
            var matrix = new QRCodeWriter().encode(target, BarcodeFormat.QR_CODE, 600, 600,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8",
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 4));
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG 编码器不可用");
            return output.toByteArray();
        } catch (WriterException e) {
            throw new IOException("群地址无法生成二维码", e);
        }
    }
}
