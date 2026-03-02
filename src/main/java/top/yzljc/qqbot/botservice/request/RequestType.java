package top.yzljc.qqbot.botservice.request;

public enum RequestType {
    GET_USER_INFO("/get_stranger_info"),
    GET_GROUP_INFO("/get_group_info"),
    GET_GROUP_LIST("/get_group_list"),
    GET_FRIEND_LIST("/get_friend_list"),
    GET_LOGIN_INFO("/get_login_info"),
    SEND_LIKE("/send_like"),
    SEND_SIGN("/send_group_sign"),
    SEND_GROUP_MSG("/send_group_msg"),
    SEND_FORWARD_MSG("/send_forward_msg"),
    SEND_PRIVATE_MSG("/send_private_msg"),
    SET_PROFILE("/set_qq_profile"),
    FORWARD_SINGLE_MSG("/forward_group_single_msg"),
    GROUP_POKE("/group_poke"),
    PUT_EMOJI("/set_msg_emoji_like"),
    ACCEPT_FRIEND_REQUEST("/set_friend_add_request"),
    RECALL_MESSAGE("/delete_msg");

    private final String requestLink;

    RequestType(String requestLink) {
        this.requestLink = requestLink;
    }

    public String getRequestLink() {
        return requestLink;
    }
}