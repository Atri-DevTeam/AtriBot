package top.yzljc.qqbot.official.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.LoadIllegalWords;
import top.yzljc.qqbot.service.thread.ThreadManager;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MinecraftNetwork {

    private static final Logger log = LoggerFactory.getLogger(MinecraftNetwork.class);
    private static final Pattern STRICT_FILTER_PATTERN = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5]");

    private static String serverId;
    private static long groupId;
    private static Socket socket;
    private static OutputStream out;
    private static SecretKey aesKey;
    private static volatile boolean running;
    private static CompletableFuture<String> pendingCommandResponse;
    private static CompletableFuture<BindResponse> pendingVerifyResponse;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Cipher rsaCipher;

    static {
        try {
            rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Cipher aesEncrypt;

    static {
        try {
            aesEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Cipher aesDecrypt;

    static {
        try {
            aesDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private MinecraftNetwork() {
        throw new UnsupportedOperationException();
    }

    // ==================== 对外 API ====================

    public static void connect(String serverId, String host, int port, long groupId, String rsaPublicKeyBase64) {
        disconnect();
        MinecraftNetwork.serverId = serverId;
        MinecraftNetwork.groupId = groupId;
        running = true;

        ThreadManager.execute(() -> {
            while (running) {
                try {
                    doConnect(host, port, rsaPublicKeyBase64);
                    log.info("[{}] 已连接到 MC 服务器", serverId);

                    Thread hb = new Thread(() -> {
                        while (running && isActive()) {
                            send("HEARTBEAT");
                            try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException e) { break; }
                        }
                    }, "McNet-HB-" + serverId);
                    hb.setDaemon(true);
                    hb.start();

                    readLoop();
                } catch (Exception e) {
                    log.error("[{}] 连接失败: {}", serverId, e.getMessage());
                } finally {
                    close();
                }
                if (running) {
                    log.info("[{}] 将在 10 秒后重连...", serverId);
                    try { TimeUnit.SECONDS.sleep(10); } catch (InterruptedException e) { break; }
                }
            }
        });
    }

    public static void disconnect() {
        running = false;
        close();
    }

    public static String sendCommand(String command) {
        if (!isActive()) {
            log.warn("发送失败，MC 服务器未连接");
            return null;
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        synchronized (MinecraftNetwork.class) {
            pendingCommandResponse = future;
        }
        send("EXEC_CMD|" + command);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[{}] 等待指令响应超时: {}", serverId, e.getMessage());
            return null;
        } finally {
            synchronized (MinecraftNetwork.class) {
                pendingCommandResponse = null;
            }
        }
    }

    /**
     * 向任意 MC 服务器发送指令（临时短连接），各自使用独立的 RSA 公钥和 secret。
     *
     * @return 控制台返回日志，失败返回 null
     */
    public static String sendCommandTo(String host, int port, String rsaPublicKeyBase64, String command) {
        try (Socket s = new Socket()) {
            try {
                s.connect(new InetSocketAddress(host, port), 5000);

                // 生成 AES 密钥
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                SecretKey tempAesKey = keyGen.generateKey();
                String aesKeyBase64 = Base64.getEncoder().encodeToString(tempAesKey.getEncoded());

                // RSA 加密认证消息
                String authMsg = "REPORTER_AUTH|" + aesKeyBase64;
                byte[] keyBytes = Base64.getDecoder().decode(rsaPublicKeyBase64);
                PublicKey pubKey = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(keyBytes));
                Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsa.init(Cipher.ENCRYPT_MODE, pubKey);
                byte[] encryptedAuth = rsa.doFinal(authMsg.getBytes(StandardCharsets.UTF_8));

                OutputStream os = s.getOutputStream();
                DataOutputStream dos = new DataOutputStream(os);
                dos.writeInt(encryptedAuth.length);
                dos.write(encryptedAuth);
                dos.flush();

                // 读取 AUTH_OK
                DataInputStream dis = new DataInputStream(s.getInputStream());
                int len = dis.readInt();
                byte[] frame = new byte[len];
                dis.readFully(frame);
                byte[] iv = new byte[16];
                System.arraycopy(frame, 0, iv, 0, 16);
                byte[] enc = new byte[len - 16];
                System.arraycopy(frame, 16, enc, 0, len - 16);
                Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aes.init(Cipher.DECRYPT_MODE, tempAesKey, new IvParameterSpec(iv));
                if (!"AUTH_OK".equals(new String(aes.doFinal(enc), StandardCharsets.UTF_8))) {
                    log.warn("[{}] 认证被拒绝", serverId);
                    return null;
                }

                // 发送 EXEC_CMD
                Cipher aesEnc = Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] sendIv = new byte[16];
                SecureRandom rand = new SecureRandom();
                rand.nextBytes(sendIv);
                aesEnc.init(Cipher.ENCRYPT_MODE, tempAesKey, new IvParameterSpec(sendIv));
                byte[] sendEncrypted = aesEnc.doFinal(("EXEC_CMD|" + command).getBytes(StandardCharsets.UTF_8));
                byte[] sendFrame = new byte[16 + sendEncrypted.length];
                System.arraycopy(sendIv, 0, sendFrame, 0, 16);
                System.arraycopy(sendEncrypted, 0, sendFrame, 16, sendEncrypted.length);
                DataOutputStream sd = new DataOutputStream(os);
                sd.writeInt(sendFrame.length);
                sd.write(sendFrame);
                sd.flush();

                // 读取 CMD_RESPONSE（5 秒超时）
                s.setSoTimeout(5000);
                Cipher aesDec = Cipher.getInstance("AES/CBC/PKCS5Padding");
                int rLen = dis.readInt();
                byte[] rFrame = new byte[rLen];
                dis.readFully(rFrame);
                byte[] rIv = new byte[16];
                System.arraycopy(rFrame, 0, rIv, 0, 16);
                byte[] rEnc = new byte[rLen - 16];
                System.arraycopy(rFrame, 16, rEnc, 0, rLen - 16);
                aesDec.init(Cipher.DECRYPT_MODE, tempAesKey, new IvParameterSpec(rIv));
                String response = new String(aesDec.doFinal(rEnc), StandardCharsets.UTF_8);

                if (response.startsWith("CMD_RESPONSE|")) {
                    return response.substring(13);
                }
                return response;
            } catch (Exception e) {
                log.error("[{}] 指令发送失败: {}", serverId, e.getMessage());
                return null;
            }
        } catch (Exception ignored) {
        }
        return "控制台返回内容为空";
    }

    public static BindResponse sendVerify(String code) {
        if (!isActive()) return null;
        CompletableFuture<BindResponse> future = new CompletableFuture<>();
        synchronized (MinecraftNetwork.class) {
            pendingVerifyResponse = future;
        }
        send("VERIFY|" + code);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[{}] 等待验证响应超时: {}", serverId, e.getMessage());
            return new BindResponse(500, null);
        } finally {
            synchronized (MinecraftNetwork.class) {
                pendingVerifyResponse = null;
            }
        }
    }

    public static boolean isActive() {
        return running && socket != null && !socket.isClosed();
    }

    // ==================== 连接逻辑 ====================

    private static void doConnect(String host, int port, String rsaPublicKeyBase64) throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        out = socket.getOutputStream();

        // 生成 AES 密钥
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        aesKey = keyGen.generateKey();
        String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKey.getEncoded());

        // RSA 加密认证消息
        String authMsg = "REPORTER_AUTH|" + aesKeyBase64;
        byte[] keyBytes = Base64.getDecoder().decode(rsaPublicKeyBase64);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedAuth = rsaCipher.doFinal(authMsg.getBytes(StandardCharsets.UTF_8));

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(encryptedAuth.length);
        dos.write(encryptedAuth);
        dos.flush();

        // 读取 AES 加密的 AUTH_OK
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        int len = dis.readInt();
        byte[] frame = new byte[len];
        dis.readFully(frame);

        byte[] iv = new byte[16];
        System.arraycopy(frame, 0, iv, 0, 16);
        byte[] encrypted = new byte[len - 16];
        System.arraycopy(frame, 16, encrypted, 0, len - 16);

        aesDecrypt.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        String response = new String(aesDecrypt.doFinal(encrypted), StandardCharsets.UTF_8);

        if (!"AUTH_OK".equals(response)) {
            throw new IOException("认证被拒绝: " + response);
        }
    }

    private static void readLoop() {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            while (running) {
                int len = dis.readInt();
                if (len <= 0 || len > 1024 * 1024) break;

                byte[] frame = new byte[len];
                dis.readFully(frame);

                byte[] iv = new byte[16];
                System.arraycopy(frame, 0, iv, 0, 16);
                byte[] encrypted = new byte[len - 16];
                System.arraycopy(frame, 16, encrypted, 0, len - 16);

                aesDecrypt.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
                String msg = new String(aesDecrypt.doFinal(encrypted), StandardCharsets.UTF_8);

                handleMessage(msg);
            }
        } catch (IOException | IllegalBlockSizeException | BadPaddingException e) {
            if (running) {
                log.warn("[{}] 连接断开: {}", serverId, e.getMessage());
            }
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleMessage(String msg) {
        if (msg.equals("HEARTBEAT_ACK")) {
            return;
        }

        if (msg.startsWith("VERIFY_RESPONSE|")) {
            String[] parts = msg.split("\\|", 3);
            int code = Integer.parseInt(parts[1]);
            String uuid = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
            CompletableFuture<BindResponse> future;
            synchronized (MinecraftNetwork.class) {
                future = pendingVerifyResponse;
            }
            if (future != null) {
                future.complete(new BindResponse(code, uuid));
                log.info("[{}] 收到验证响应, code: {}, uuid: {}", serverId, code, uuid);
            }
            return;
        }

        if (msg.startsWith("CMD_RESPONSE|")) {
            String logs = msg.substring(13);
            CompletableFuture<String> future;
            synchronized (MinecraftNetwork.class) {
                future = pendingCommandResponse;
            }
            if (future != null) {
                future.complete(logs);
                log.info("[{}] 收到指令反馈，长度: {}", serverId, logs.length());
            }
            return;
        }

        if (msg.startsWith("Player|")) {
            String[] parts = msg.split("\\|", 4);
            if (parts.length >= 3) {
                String action = parts[1];
                String playerName = parts[2];
                String text = "";
                if ("JOIN".equalsIgnoreCase(action)) {
                    text = "玩家 " + playerName + " 加入了服务器";
                } else if ("QUIT".equalsIgnoreCase(action)) {
                    text = "玩家 " + playerName + " 离开了服务器";
                }
                if (!text.isEmpty()) {
                    sendToGroup(text);
                }
            }
            return;
        }

        if (msg.startsWith("Chat|")) {
            String[] parts = msg.split("\\|", 3);
            if (parts.length >= 3) {
                String playerName = parts[1];
                String chatMsg = parts[2];

                boolean dirty = LoadIllegalWords.containsSensitiveWord(chatMsg);
                if (!dirty) {
                    String cleaned = STRICT_FILTER_PATTERN.matcher(chatMsg).replaceAll("");
                    dirty = LoadIllegalWords.containsSensitiveWord(cleaned);
                }
                if (dirty) {
                    log.info("[{}] 拦截违规消息 - {}: {}", serverId, playerName, chatMsg);
                    sendToGroup("有违规聊天内容已进行拦截，请管理员进行审查！");
                    return;
                }

                sendToGroup(playerName + ": " + chatMsg);
            }
        }
    }

    private static synchronized void send(String data) {
        if (!isActive()) return;
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            aesEncrypt.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            byte[] encrypted = aesEncrypt.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] frame = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, frame, 0, 16);
            System.arraycopy(encrypted, 0, frame, 16, encrypted.length);

            DataOutputStream dos = new DataOutputStream(out);
            dos.writeInt(frame.length);
            dos.write(frame);
            dos.flush();
        } catch (Exception e) {
            log.error("[{}] 发送失败: {}", serverId, e.getMessage());
            close();
        }
    }

    private static synchronized void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        out = null;
        aesKey = null;
    }

    // ==================== 消息转发 ====================

    private static void sendToGroup(String message) {
        if (groupId != 0L) {
            GroupMessage.chatMessage(groupId, message);
        }
        log.info("[MC消息] {}", message);
    }
}
