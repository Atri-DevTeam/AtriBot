package top.yzljc.atribot.utils.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftRemote;

import javax.crypto.Cipher;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
public class MinecraftSocket {

    private final String serverIp;
    private final int serverPort;
    private final PublicKey publicKey;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public MinecraftSocket(String serverIp, int serverPort, String publicKeyBase64) throws Exception {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.publicKey = keyFactory.generatePublic(spec);
    }

    public BindResponse sendRequest(String code) {
        // 优先走 AES 长连接
        BindResponse aesResponse = MinecraftRemote.sendVerify(code);
        if (aesResponse != null) {
            return aesResponse;
        }

        // 回退 RSA 短连接
        try (Socket socket = new Socket(serverIp, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            socket.setSoTimeout(5000);

            byte[] messageBytes = code.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedData = cipher.doFinal(messageBytes);

            dos.writeInt(encryptedData.length);
            dos.write(encryptedData);
            dos.flush();

            // 读取长度 + JSON 数据
            int length = dis.readInt();
            if (length <= 0 || length > 1024) {
                return new BindResponse(500, null);
            }

            byte[] jsonBytes = new byte[length];
            dis.readFully(jsonBytes);
            String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);

            // Jackson 解析 JSON
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            int statusCode = jsonNode.get("code").asInt();
            String uuid = (jsonNode.has("uuid") && !jsonNode.get("uuid").asText().isEmpty())
                    ? jsonNode.get("uuid").asText()
                    : null;

            log.info("Minecraft 验证请求完成，状态码: {}, UUID: {}", statusCode, uuid);
            return new BindResponse(statusCode, uuid);

        } catch (Exception e) {
            log.error("Minecraft 验证请求失败", e);
            return new BindResponse(500, null);
        }
    }
}