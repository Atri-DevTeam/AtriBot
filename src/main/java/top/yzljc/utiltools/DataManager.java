package top.yzljc.utiltools;

import java.util.*;
import java.io.*;

/**
 * 服务器列表存取，可序列化文件
 */
public class DataManager {
    private static final String DATA_FILE = "servers.dat";

    public static List<ServerInfo> load() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            Object obj = ois.readObject();
            //noinspection unchecked
            return (List<ServerInfo>) obj;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void save(List<ServerInfo> list) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(list);
        } catch (Exception e) {
            System.err.println("保存失败:" + e.getMessage());
        }
    }
}