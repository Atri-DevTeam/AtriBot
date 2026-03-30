package top.yzljc.qqbot.socket;

import javax.crypto.Cipher;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class MinecraftVariety {

    private final String serverIp;
    private final int serverPort;
    private final PublicKey publicKey;

    public MinecraftVariety(String serverIp, int serverPort, String publicKeyBase64) throws Exception {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.publicKey = keyFactory.generatePublic(spec);
    }

    /**
     * 发送验证请求给 MC 服务器并获取处理结果
     *
     * @param qq   玩家的 QQ 号
     * @param code 玩家输入的 6 位验证码
     * @return 200(成功) | 100(已绑定) | 400(无效验证码) | 500(网络/服务器错误)
     */
    public int sendRequest(long qq, String code) {
        try (Socket socket = new Socket(serverIp, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(5000);
            String message = code + ":" + qq;
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedData = cipher.doFinal(messageBytes);

            dos.writeInt(encryptedData.length);
            dos.write(encryptedData);
            dos.flush();
            return dis.readInt();

        } catch (Exception e) {
            return 500;
        }
    }
}