package top.yzljc.atribot.platform.napcat;

import lombok.Getter;

@Getter
public enum RequestType {
    GET_USER_INFO("/get_stranger_info"),
    GET_GROUP_INFO("/get_group_info"),
    GET_GROUP_LIST("/get_group_list"),
    GET_FRIEND_LIST("/get_friend_list"),
    GET_LOGIN_INFO("/get_login_info"),
    GET_GROUP_MEMBER_INFO("/get_group_member_info"),
    SEND_LIKE("/send_like"),
    SEND_SIGN("/send_group_sign"),
    SEND_GROUP_MSG("/send_group_msg"),
    SEND_GROUP_FORWARD_MSG("/send_forward_msg"),
    SEND_PRIVATE_FORWARD_MSG("/send_private_forward_msg"),
    SEND_PRIVATE_MSG("/send_private_msg"),
    SET_PROFILE("/set_qq_profile"),
    FORWARD_SINGLE_MSG("/forward_group_single_msg"),
    GROUP_POKE("/group_poke"),
    PUT_EMOJI("/set_msg_emoji_like"),
    ACCEPT_FRIEND_REQUEST("/set_friend_add_request"),
    HANDLE_GROUP_PENDING_REQUEST("/set_group_add_request"),
    QUIT_GROUP("/set_group_leave"),
    SET_GROUP_KICK_MEMBER("/set_group_kick_members"),
    RECALL_MESSAGE("/delete_msg");

    private final String requestLink;

    RequestType(String requestLink) {
        this.requestLink = requestLink;
    }

}