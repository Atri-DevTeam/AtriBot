# 频道第二账号静态调用索引

业务方法接受具体参数。请求组装留在内部，返回 ChannelCliResult。聊天入口见 [/ema 指令目录](ema-command.md)。

```java
ChannelManagement.joinGuild(guildId);
ChannelMembers.muteMember(guildId, tinyId, Duration.ofMinutes(10));
ChannelMembers.unmuteMember(guildId, tinyId);
ChannelMembers.kickMembers(guildId, List.of("456", "789"), true, null, ChannelCliOptions.CONFIRMED);

ChannelPosts.publishText(guildId, channelId, "正文", "标题", ChannelCliOptions.DRY_RUN);
ChannelPosts.sendMessage(guildId, channelId, ImageComponent.imageOf(imageUrl));
ChannelPosts.sendMessage(guildId, channelId, "标题", new Markdown("正文"));
ChannelPrivateChat.sendMessage(guildId, tinyId, "你好");

ChannelComments.sendComment(1, "评论正文");
ChannelComments.sendReply(1, "回复正文");
var feed = new ChannelComments.FeedTarget(guildId, channelId, feedId, feedCreateTime);
ChannelComments.deleteComment(feed, commentId, commentAuthorId);

```

带 `ChannelCliOptions` 的重载可指定确认和预演，不带时使用默认选项。下表列出每个公开签名；`FeedTarget` 包含频道、版块、帖子 ID 和创建时间，`CommentTarget` 再包含作者及评论定位信息。创建时间使用接口返回值，禁言使用 `Duration`。

## ChannelPosts

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/ChannelPosts.java)

| 方法 | 参数 |
| --- | --- |
| `sendMessage` | `String guildId, String channelId, String text` |
| `sendMessage` | `String guildId, String channelId, ImageComponent image` |
| `sendMessage` | `String guildId, String channelId, String title, Markdown markdown` |
| `getGuildFeeds` | `String guildId, Integer getType, Integer count, String feedAttachInfo` |
| `getChannelTimelineFeeds` | `String guildId, String channelId, Integer count, String feedAttachInfo` |
| `getFeedDetail` | `String feedId, String guildId, String channelId` |
| `getFeedShareUrl` | `String feedId, String guildId, String channelId` |
| `doFeedPrefer` | `String feedId, Integer action, String guildId, String channelId` |
| `delFeed` | `String feedId, String guildId, String channelId, String createTime, boolean confirmed` |
| `setFeedEssence` | `String feedId, Integer action` |
| `pushEssenceFeed` | `String feedId` |
| `moveFeed` | `String guildId, String channelId, String originalChannelId, String feedId` |
| `getGuildFeeds` | `String guildId, Integer getType, Integer count, String feedAttachInfo, ChannelCliOptions options` |
| `getChannelTimelineFeeds` | `String guildId, String channelId, Integer count, String feedAttachInfo, ChannelCliOptions options` |
| `getPost` | `String guildId, String channelId, String feedId` |
| `getPost` | `String guildId, String channelId, String feedId, ChannelCliOptions options` |
| `searchGuildFeeds` | `String guildId, String query, String nextPageCookie` |
| `searchGuildFeeds` | `String guildId, String query, String nextPageCookie, ChannelCliOptions options` |
| `sharePost` | `String guildId, String channelId, String feedId` |
| `sharePost` | `String guildId, String channelId, String feedId, ChannelCliOptions options` |
| `publishText` | `String guildId, String channelId, String content, String title` |
| `publishText` | `String guildId, String channelId, String content, String title, ChannelCliOptions options` |
| `publishImages` | `String guildId, String channelId, List<String> filePaths, String content, String title` |
| `publishImages` | `String guildId, String channelId, List<String> filePaths, String content, String title, ChannelCliOptions options` |
| `deleteFeed` | `String guildId, String channelId, String feedId, String createTime` |
| `deleteFeed` | `String guildId, String channelId, String feedId, String createTime, ChannelCliOptions options` |
| `likeFeed` | `String guildId, String channelId, String feedId` |
| `likeFeed` | `String guildId, String channelId, String feedId, ChannelCliOptions options` |
| `unlikeFeed` | `String guildId, String channelId, String feedId` |
| `unlikeFeed` | `String guildId, String channelId, String feedId, ChannelCliOptions options` |
| `editText` | `String guildId, String channelId, String feedId, String createTime, String content, String title` |
| `editText` | `String guildId, String channelId, String feedId, String createTime, String content, String title, ChannelCliOptions options` |
| `editMarkdown` | `String guildId, String channelId, String feedId, String createTime, String markdownContent, String title` |
| `editMarkdown` | `String guildId, String channelId, String feedId, String createTime, String markdownContent, String title, ChannelCliOptions options` |
| `pinFeed` | `String guildId, String feedId, String userId, String createTime` |
| `pinFeed` | `String guildId, String feedId, String userId, String createTime, ChannelCliOptions options` |
| `unpinFeed` | `String guildId, String feedId, String userId, String createTime` |
| `unpinFeed` | `String guildId, String feedId, String userId, String createTime, ChannelCliOptions options` |
| `markEssence` | `String feedId` |
| `markEssence` | `String feedId, ChannelCliOptions options` |
| `removeEssence` | `String feedId` |
| `removeEssence` | `String feedId, ChannelCliOptions options` |
| `pushEssenceFeed` | `String feedId, ChannelCliOptions options` |
| `movePost` | `String guildId, String originalChannelId, String channelId, String feedId` |
| `movePost` | `String guildId, String originalChannelId, String channelId, String feedId, ChannelCliOptions options` |
| `quickPublishText` | `String content, String title` |
| `quickPublishText` | `String content, String title, ChannelCliOptions options` |
| `resumeQuickPublish` | `String resumeId, String pick` |
| `resumeQuickPublish` | `String resumeId, String pick, ChannelCliOptions options` |
| `latestFeedsDetail` | `Integer count` |
| `latestFeedsDetail` | `Integer count, ChannelCliOptions options` |
| `resumeLatestFeedsDetail` | `String resumeId, String pick` |
| `resumeLatestFeedsDetail` | `String resumeId, String pick, ChannelCliOptions options` |
| `hotFeedsDetail` | `Integer count` |
| `hotFeedsDetail` | `Integer count, ChannelCliOptions options` |
| `resumeHotFeedsDetail` | `String resumeId, String pick` |
| `resumeHotFeedsDetail` | `String resumeId, String pick, ChannelCliOptions options` |

## ChannelComments

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/ChannelComments.java)

| 方法 | 参数 |
| --- | --- |
| `sendComment` | `FeedTarget target, String text` |
| `sendComment` | `FeedTarget target, String text, Path imagePath` |
| `sendReply` | `CommentTarget target, String replierId, String text` |
| `sendReply` | `CommentTarget target, String replierId, String text, Path imagePath` |
| `sendComment` | `int noticeRef, String text` |
| `sendReply` | `int noticeRef, String text` |
| `getFeedComments` | `String guildId, String channelId, String feedId, Integer count, Integer rankType, Integer replyListNum, String attachInfo` |
| `getFeedComments` | `String guildId, String channelId, String feedId, Integer count, Integer rankType, Integer replyListNum, String attachInfo, ChannelCliOptions options` |
| `getNextPageReplies` | `String guildId, String channelId, String feedId, String commentId, Integer count, String attachInfo` |
| `getNextPageReplies` | `String guildId, String channelId, String feedId, String commentId, Integer count, String attachInfo, ChannelCliOptions options` |
| `sendComment` | `Integer ref, String content, String imagePath` |
| `sendComment` | `Integer ref, String content, String imagePath, ChannelCliOptions options` |
| `sendReply` | `Integer ref, String content, String imagePath` |
| `sendReply` | `Integer ref, String content, String imagePath, ChannelCliOptions options` |
| `sendCommentTo` | `FeedTarget target, String content, String imagePath` |
| `sendCommentTo` | `FeedTarget target, String content, String imagePath, ChannelCliOptions options` |
| `deleteComment` | `FeedTarget target, String commentId, String commentAuthorId` |
| `deleteComment` | `FeedTarget target, String commentId, String commentAuthorId, ChannelCliOptions options` |
| `deleteCommentAsOwner` | `FeedTarget target, String commentId, String commentAuthorId` |
| `deleteCommentAsOwner` | `FeedTarget target, String commentId, String commentAuthorId, ChannelCliOptions options` |
| `searchAndComment` | `String guildId, String query, String content` |
| `searchAndComment` | `String guildId, String query, String content, ChannelCliOptions options` |
| `resumeSearchAndComment` | `String resumeId, String pick` |
| `resumeSearchAndComment` | `String resumeId, String pick, ChannelCliOptions options` |
| `searchDeleteAndMute` | `String guildId, String query, String timeStamp, String set` |
| `searchDeleteAndMute` | `String guildId, String query, String timeStamp, String set, ChannelCliOptions options` |
| `resumeDeleteAndMute` | `String resumeId, String pick, String timeStamp, String set` |
| `resumeDeleteAndMute` | `String resumeId, String pick, String timeStamp, String set, ChannelCliOptions options` |
| `likeComment` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId` |
| `likeComment` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, ChannelCliOptions options` |
| `unlikeComment` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId` |
| `unlikeComment` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, ChannelCliOptions options` |
| `likeReply` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId` |
| `likeReply` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId, ChannelCliOptions options` |
| `unlikeReply` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId` |
| `unlikeReply` | `FeedTarget target, String feedAuthorId, String commentId, String commentAuthorId, String replyId, String replyAuthorId, ChannelCliOptions options` |
| `deleteReply` | `CommentTarget target, String replyId, String replierId` |
| `deleteReply` | `CommentTarget target, String replyId, String replierId, ChannelCliOptions options` |
| `deleteReplyAsOwner` | `CommentTarget target, String replyId, String replierId` |
| `deleteReplyAsOwner` | `CommentTarget target, String replyId, String replierId, ChannelCliOptions options` |
| `sendReplyTo` | `CommentTarget target, String replierId, String content, String imagePath` |
| `sendReplyTo` | `CommentTarget target, String replierId, String content, String imagePath, ChannelCliOptions options` |

## ChannelInformation

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/ChannelInformation.java)

| 方法 | 参数 |
| --- | --- |
| `getJoinedGuilds` | — |
| `getGuildInfo` | `String guildId` |
| `getChannelList` | `String guildId` |
| `getMyJoinGuildInfo` | — |
| `getJoinGuildSetting` | `String guildId` |
| `getGuildShareUrl` | `String guildId` |
| `getShareInfo` | `String url` |
| `searchGuildContent` | `String keyword, String scope, String nextPageToken` |
| `getGuildInfo` | `String guildId, ChannelCliOptions options` |
| `getMyJoinGuildInfo` | `ChannelCliOptions options` |
| `getGuildChannelList` | `String guildId` |
| `getGuildChannelList` | `String guildId, ChannelCliOptions options` |
| `searchGuildContent` | `String keyword, String scope, String nextPageToken, ChannelCliOptions options` |
| `getJoinGuildSetting` | `String guildId, ChannelCliOptions options` |
| `getGuildShareUrl` | `String guildId, ChannelCliOptions options` |
| `getShareInfo` | `String url, ChannelCliOptions options` |

## ChannelMembers

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/management/ChannelMembers.java)

| 方法 | 参数 |
| --- | --- |
| `muteMember` | `String guildId, String tinyId, Duration duration` |
| `unmuteMember` | `String guildId, String tinyId` |
| `kickMember` | `String guildId, String tinyId, boolean blacklist, String revokeMsgs, boolean confirmed` |
| `getUserInfo` | `String guildId, String tinyId` |
| `getGuildMemberList` | `String guildId, String nextPageToken` |
| `guildMemberSearch` | `String guildId, String keyword, Integer num, String nextPos` |
| `getUserInfo` | `String guildId, String tinyId, ChannelCliOptions options` |
| `getGuildMemberList` | `String guildId, String nextPageToken, ChannelCliOptions options` |
| `guildMemberSearch` | `String guildId, String keyword, Integer num, String nextPos, ChannelCliOptions options` |
| `kickMembers` | `String guildId, List<String> memberTinyids, Boolean blacklist, String revokeMsgs` |
| `kickMembers` | `String guildId, List<String> memberTinyids, Boolean blacklist, String revokeMsgs, ChannelCliOptions options` |
| `muteMember` | `String guildId, String tinyId, Duration duration, ChannelCliOptions options` |
| `unmuteMember` | `String guildId, String tinyId, ChannelCliOptions options` |

## ChannelRoles

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/management/ChannelRoles.java)

| 方法 | 参数 |
| --- | --- |
| `createGuildRoleGroup` | `String guildId, String name` |
| `modifyGuildRoleGroup` | `String guildId, String roleId, String name` |
| `addRoleMembers` | `String guildId, String roleId, List<String> tinyIds` |
| `removeRoleMembers` | `String guildId, String roleId, List<String> tinyIds, boolean confirmed` |
| `addAdmin` | `String guildId, List<String> tinyIds` |
| `removeAdmin` | `String guildId, List<String> tinyIds, boolean confirmed` |
| `createGuildRoleGroup` | `String guildId, String name, ChannelCliOptions options` |
| `modifyGuildRoleGroup` | `String guildId, String roleId, String name, ChannelCliOptions options` |
| `addRoleMembers` | `String guildId, String roleId, List<String> tinyIds, ChannelCliOptions options` |
| `removeRoleMembers` | `String guildId, String roleId, List<String> tinyIds` |
| `removeRoleMembers` | `String guildId, String roleId, List<String> tinyIds, ChannelCliOptions options` |
| `addAdmin` | `String guildId, List<String> tinyIds, ChannelCliOptions options` |
| `removeAdmin` | `String guildId, List<String> tinyIds` |
| `removeAdmin` | `String guildId, List<String> tinyIds, ChannelCliOptions options` |

## ChannelManagement

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/management/ChannelManagement.java)

| 方法 | 参数 |
| --- | --- |
| `updateGuildInfo` | `String guildId, String guildName, String guildProfile` |
| `modifyGuildNumber` | `String guildId, String guildNumber` |
| `joinGuild` | `String guildId` |
| `createChannel` | `String guildId, String channelName` |
| `deleteChannel` | `String guildId, List<String> channelIds, boolean confirmed` |
| `modifyChannel` | `String guildId, String channelId, String channelName` |
| `uploadGuildAvatar` | `String guildId, String imagePath` |
| `createThemePrivateGuild` | `String imagePath, String theme, String guildName, String guildProfile, String communityType` |
| `updateJoinGuildSetting` | `String guildId, String joinType` |
| `leaveGuild` | `String guildId, boolean confirmed` |
| `updateGuildInfo` | `String guildId, String guildName, String guildProfile, ChannelCliOptions options` |
| `modifyGuildNumber` | `String guildId, String guildNumber, ChannelCliOptions options` |
| `joinGuild` | `String guildId, ChannelCliOptions options` |
| `createChannel` | `String guildId, String channelName, ChannelCliOptions options` |
| `deleteChannel` | `String guildId, List<String> channelIds` |
| `deleteChannel` | `String guildId, List<String> channelIds, ChannelCliOptions options` |
| `modifyChannel` | `String guildId, String channelId, String channelName, ChannelCliOptions options` |
| `uploadGuildAvatar` | `String guildId, String imagePath, ChannelCliOptions options` |
| `createThemePrivateGuild` | `String imagePath, String theme, String guildName, String guildProfile, String communityType, ChannelCliOptions options` |
| `updateJoinGuildSetting` | `String guildId, String joinType, ChannelCliOptions options` |
| `leaveGuild` | `String guildId` |
| `leaveGuild` | `String guildId, ChannelCliOptions options` |
| `searchAndJoin` | `String keyword` |
| `searchAndJoin` | `String keyword, ChannelCliOptions options` |
| `resumeSearchAndJoin` | `String resumeId, String pick` |
| `resumeSearchAndJoin` | `String resumeId, String pick, ChannelCliOptions options` |

## ChannelPrivateChat

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/ChannelPrivateChat.java)

| 方法 | 参数 |
| --- | --- |
| `sendMessage` | `String guildId, String tinyId, String text` |
| `replyMessage` | `int noticeRef, String text` |
| `sendMessage` | `String sourceGuildId, String peerTinyId, String text, ChannelCliOptions options` |
| `replyMessage` | `Integer ref, String text` |
| `replyMessage` | `Integer ref, String text, ChannelCliOptions options` |

## ChannelNotices

[源文件](../src/main/java/top/yzljc/sakuraba_ema/guild/ChannelNotices.java)

| 方法 | 参数 |
| --- | --- |
| `noticesStatus` | — |
| `checkNotices` | — |
| `subscribeNotices` | — |
| `unsubscribeNotices` | — |
| `checkNewNotices` | — |
| `getRecentNotices` | — |
| `getNotices` | `Integer pageNum, String guildId, String attachInfo` |
| `getNotices` | `Integer pageNum, String guildId, String attachInfo, ChannelCliOptions options` |
| `noticesOn` | `String sessionKey, Boolean confirm` |
| `noticesOn` | `String sessionKey, Boolean confirm, ChannelCliOptions options` |
| `noticesOff` | `String sessionKey` |
| `noticesOff` | `String sessionKey, ChannelCliOptions options` |
| `noticesStatus` | `ChannelCliOptions options` |
| `checkNotices` | `ChannelCliOptions options` |
| `subscribeNotices` | `ChannelCliOptions options` |
| `unsubscribeNotices` | `ChannelCliOptions options` |
| `checkNewNotices` | `ChannelCliOptions options` |
| `getRecentNotices` | `ChannelCliOptions options` |
| `agreeNotice` | `Integer ref` |
| `agreeNotice` | `Integer ref, ChannelCliOptions options` |
| `refuseNotice` | `Integer ref` |
| `refuseNotice` | `Integer ref, ChannelCliOptions options` |
| `agreeNotice` | `String noticeId` |
| `agreeNotice` | `String noticeId, ChannelCliOptions options` |
| `refuseNotice` | `String noticeId` |
| `refuseNotice` | `String noticeId, ChannelCliOptions options` |

## ChannelSystem

[源文件](../src/main/java/top/yzljc/sakuraba_ema/ChannelSystem.java)

| 方法 | 参数 |
| --- | --- |
| `version` | — |
| `doctor` | — |
| `login` | — |
| `login` | `Path qrcodePath, boolean forceRelogin` |
| `pollToken` | — |
| `loginStatus` | — |
| `schema` | — |
| `schema` | `String commandPath` |
| `searchSchema` | `String keyword` |

`ChannelCliResult.success()` 表示执行结果，失败查看 `getError()`，查询数据查看 `getData()`。CLI 的串行执行、限流等待与超时处理沿用原客户端。
