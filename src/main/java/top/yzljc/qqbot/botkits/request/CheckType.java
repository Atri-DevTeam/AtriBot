package top.yzljc.qqbot.botkits.request;

public enum CheckType {
    GET_USER_INFO("/get_stranger_info", "user_id"),
    GET_GROUP_INFO("/get_group_info", "group_id"),
    SEND_SIGN("/send_group_sign", "group_id"),
    RECALL_MESSAGE("/delete_msg", "message_id");

    private final String requestLink;
    private final String requestData;

    CheckType(String requestLink, String requestData) {
        this.requestLink = requestLink;
        this.requestData = requestData;
    }

    public String getRequestLink() {
        return requestLink;
    }

    public String getRequestDataType() {
        return requestData;
    }
}