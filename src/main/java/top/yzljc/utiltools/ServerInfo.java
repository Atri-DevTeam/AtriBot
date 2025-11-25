package top.yzljc.utiltools;

import java.io.Serializable;

public class ServerInfo implements Serializable {
    private String name;
    private String ip;
    private int port;
    private boolean online;
    private boolean firstCheck = true;

    public ServerInfo(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }
    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public boolean isOnline() { return online; }
    public boolean isFirstCheck() { return firstCheck; }
    public void setOnline(boolean online) { this.online = online; }
    public void setFirstCheck(boolean firstCheck) { this.firstCheck = firstCheck; }
}