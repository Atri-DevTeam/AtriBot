# /ema 管理指令

仅机器人管理员可用。

```text
/ema help
/ema help member mute
/ema guild join 123
/ema member mute 123 456 10m
/ema member unmute 123 456
/ema member kick --yes --blacklist 123 456,789
/ema dm send 123 456 你好 世界
/ema post publish --title "今日公告" 123 456 第一行\n第二行
/ema result 2
```

## 指令目录

可选项放在位置参数前。`<正文...>` 接收剩余文字；含空格的单个参数用引号括起来。ID 列表用逗号分隔。

| 指令 | 位置参数 | 可选项 |
| --- | --- | --- |
| `guild info` | `<频道ID>` | — |
| `guild list` | — | — |
| `guild channel list` | `<频道ID>` | — |
| `guild search` | `<关键词...>` | `--scope <值> --next-page-token <值>` |
| `guild admission get` | `<频道ID>` | — |
| `guild share url` | `<频道ID>` | — |
| `guild share info` | `<链接>` | — |
| `guild edit` | `<频道ID>` | `--guild-name <值> --guild-profile <值>` |
| `guild number` | `<频道ID> <频道号>` | — |
| `guild join` | `<频道ID>` | — |
| `guild channel create` | `<频道ID> <版块名称...>` | — |
| `guild channel delete` | `<频道ID> <版块ID列表>` | — |
| `guild channel edit` | `<频道ID> <版块ID> <版块名称...>` | — |
| `guild avatar` | `<频道ID> <图片路径>` | — |
| `guild create` | `<图片路径> <频道名称...>` | `--theme <值> --guild-profile <值> --community-type <值>` |
| `guild admission set` | `<频道ID> <入频方式>` | — |
| `guild leave` | `<频道ID>` | — |
| `guild search-join` | `<关键词...>` | — |
| `guild search-join resume` | `<续办ID> <选择编号>` | — |
| `member get` | `<频道ID> <成员ID>` | — |
| `member list` | `<频道ID>` | `--next-page-token <值>` |
| `member search` | `<频道ID> <关键词...>` | `--num <值> --next-pos <值>` |
| `member kick` | `<频道ID> <成员ID列表>` | `--blacklist --revoke-msgs <值>` |
| `member mute` | `<频道ID> <成员ID> <时长>` | — |
| `member unmute` | `<频道ID> <成员ID>` | — |
| `role create` | `<频道ID> <名称...>` | — |
| `role edit` | `<频道ID> <身份组ID> <名称...>` | — |
| `role member add` | `<频道ID> <身份组ID> <成员ID列表>` | — |
| `role member remove` | `<频道ID> <身份组ID> <成员ID列表>` | — |
| `role admin add` | `<频道ID> <成员ID列表>` | — |
| `role admin remove` | `<频道ID> <成员ID列表>` | — |
| `dm send` | `<频道ID> <用户ID> <正文...>` | — |
| `dm reply` | `<通知编号> <正文...>` | — |
| `notice interactions` | — | `--page-num <值> --guild-id <值> --attach-info <值>` |
| `notice push on` | — | `--session-key <值> --confirm` |
| `notice push off` | — | `--session-key <值>` |
| `notice push status` | — | — |
| `notice check` | — | — |
| `notice subscribe` | — | — |
| `notice unsubscribe` | — | — |
| `notice check-new` | — | — |
| `notice recent` | — | — |
| `notice agree` | `<通知编号>` | — |
| `notice refuse` | `<通知编号>` | — |
| `notice agree-id` | `<通知ID>` | — |
| `notice refuse-id` | `<通知ID>` | — |
| `post list` | `<频道ID>` | `--get-type <值> --count <值> --feed-attach-info <值>` |
| `post channel list` | `<频道ID> <版块ID>` | `--count <值> --feed-attach-info <值>` |
| `post get` | `<频道ID> <版块ID> <帖子ID>` | — |
| `post search` | `<频道ID> <关键词...>` | `--next-page-cookie <值>` |
| `post share` | `<频道ID> <版块ID> <帖子ID>` | — |
| `post publish` | `<频道ID> <版块ID> <正文...>` | `--title <值>` |
| `post publish images` | `<频道ID> <版块ID> <图片路径列表> <正文...>` | `--title <值>` |
| `post delete` | `<频道ID> <版块ID> <帖子ID> <创建时间戳>` | — |
| `post like` | `<频道ID> <版块ID> <帖子ID>` | — |
| `post unlike` | `<频道ID> <版块ID> <帖子ID>` | — |
| `post edit` | `<频道ID> <版块ID> <帖子ID> <创建时间戳> <正文...>` | `--title <值>` |
| `post edit markdown` | `<频道ID> <版块ID> <帖子ID> <创建时间戳> <Markdown正文...>` | `--title <值>` |
| `post pin` | `<频道ID> <帖子ID> <作者ID> <创建时间戳>` | — |
| `post unpin` | `<频道ID> <帖子ID> <作者ID> <创建时间戳>` | — |
| `post essence set` | `<帖子ID>` | — |
| `post essence remove` | `<帖子ID>` | — |
| `post essence push` | `<帖子ID>` | — |
| `post move` | `<频道ID> <原版块ID> <版块ID> <帖子ID>` | — |
| `post quick publish` | `<正文...>` | `--title <值>` |
| `post quick resume` | `<续办ID> <选择编号>` | — |
| `post latest` | — | `--count <值>` |
| `post latest resume` | `<续办ID> <选择编号>` | — |
| `post hot` | — | `--count <值>` |
| `post hot resume` | `<续办ID> <选择编号>` | — |
| `comment list` | `<频道ID> <版块ID> <帖子ID>` | `--count <值> --rank-type <值> --reply-list-num <值> --attach-info <值>` |
| `comment replies` | `<频道ID> <版块ID> <帖子ID> <评论ID>` | `--count <值> --attach-info <值>` |
| `comment send` | `<通知编号> <正文...>` | `--image-path <值>` |
| `comment reply` | `<通知编号> <正文...>` | `--image-path <值>` |
| `comment send-to` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <正文...>` | `--image-path <值>` |
| `comment delete` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <评论ID> <评论作者ID>` | — |
| `comment delete-by-owner` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <评论ID> <评论作者ID>` | — |
| `comment search send` | `<频道ID> <关键词> <正文...>` | — |
| `comment search resume` | `<续办ID> <选择编号>` | — |
| `comment delete mute` | `<频道ID> <关键词...>` | `--time-stamp <值> --set <值>` |
| `comment delete mute resume` | `<续办ID> <选择编号>` | `--time-stamp <值> --set <值>` |
| `comment like` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID>` | — |
| `comment unlike` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID>` | — |
| `comment reply-like` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID> <回复ID> <回复作者ID>` | — |
| `comment reply-unlike` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID> <回复ID> <回复作者ID>` | — |
| `comment reply-delete` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID> <评论创建时间戳> <回复ID> <回复人ID>` | — |
| `comment reply-delete-by-owner` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID> <评论创建时间戳> <回复ID> <回复人ID>` | — |
| `comment reply-to` | `<频道ID> <版块ID> <帖子ID> <帖子创建时间戳> <帖子作者ID> <评论ID> <评论作者ID> <评论创建时间戳> <回复人ID> <正文...>` | `--image-path <值>` |
| `system version` | — | — |
| `system doctor` | — | — |
| `system login start` | — | `--qrcode-path <值> --force` |
| `system login poll` | — | — |
| `system login status` | — | — |
| `system schema list` | — | — |
| `system schema get` | `<命令名>` | — |
| `system schema search` | `<关键词...>` | — |
| `post send text` | `<频道ID> <版块ID> <正文...>` | — |
| `post send image` | `<频道ID> <版块ID> <链接>` | `--content <值>` |
| `post send markdown` | `<频道ID> <版块ID> <标题> <正文...>` | — |

## 执行选项

业务指令支持 `--yes` 和 `--dry-run`；`post send`、`system` 使用的现有方法不支持这两个选项。强制重新登录用 `/ema system login start --force`。

后台常驻通知服务及未提供具体参数入口的高级媒体编辑选项不在指令中开放。

Java 方法见 [静态调用索引](sakuraba-ema-calls.md)。

