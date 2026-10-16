# 消息

[文档目录](../README.md)

## 用户与会话

| 场景 | 用户 | 消息 | 会话标识 |
| --- | --- | --- | --- |
| QQ 群聊 | `QQUser` | `QQMessage` | 群 OpenID |
| QQ C2C | `QQUser` | `QQMessage` | 用户 OpenID |
| QQ 文字子频道 | `QQGuildUser` | `QQGuildMessage` | 子频道 ID |
| QQ 频道私信 | `QQGuildUser` | `QQGuildMessage` | 私信会话的 guildId |
| Napcat 群聊 | `NapcatUser` | `NapcatMessage` | 群号码 |
| Napcat 私聊 | `NapcatUser` | `NapcatMessage` | 用户 QQ 号码 |

`User` 提供平台、用户标识、名称、机器人标记、账号查询和权限判断，`Message` 提供平台、消息 ID、内容、时间戳和提及用户列表

平台消息中缺失的数据不会由框架保证补全，时间戳保持平台来源格式，不是统一的时间对象

::: important 频道用户标识
QQ 频道用户的 `getUserId()` 是频道用户 ID，`getUserOpenId()` 是事件提供的统一用户标识，可能为空，两者不能替换使用
:::

::: note 平台角色与机器人权限
`isPlatformAdmin()` 查询平台角色，`hasPermission()` 查询机器人管理权限，群主身份不会自动等同于机器人管理员
:::

`QQUser.isBlocked()` 合并封禁与忽略状态，`QQGuildUser` 和 `NapcatUser` 当前默认返回 false，业务限制需要由运行服务补充

`Platform` 保留部分外部适配器使用的枚举值，不表示 core 内包含 Discord 或 KOOK 连接实现

## 功能目录

| 分类 | 文档 |
| --- | --- |
| QQ 消息 | [调用约定](qq/messages/conventions.md)、[群聊](qq/messages/group.md)、[C2C](qq/messages/c2c.md)、[频道](qq/messages/guild.md) |
| 上传与流式 | [图片与文件](qq/messages/media.md)、[备用上传](qq/messages/backup-upload.md)、[流式消息](qq/messages/stream.md) |
| 消息组件 | [Markdown](qq/components/markdown.md)、[按钮](qq/components/buttons.md)、[图文卡片](qq/components/cards.md)、[Ark](qq/components/ark.md)、[Embed](qq/components/embed.md) |
| 交互配置 | [自定义菜单](qq/interaction/menu.md)、[指令面板](qq/interaction/panel.md)、[按钮交互](qq/interaction/callback.md) |
| 群管理 | [禁言](qq/management/mute.md)、[成员](qq/management/members.md)、[黑名单](qq/management/blacklist.md)、[申请](qq/management/join-requests.md)、[策略](qq/management/join-strategies.md) |
| Napcat | [消息](napcat/messages.md)、[消息段](napcat/segments.md)、[转发](napcat/forwarding.md)、[群操作](napcat/groups.md)、[用户](napcat/users.md)、[请求](napcat/requests.md) |
| 底层发送 | [MessageBody 与 ChatService](qq/messages/advanced.md) |

## 频道帖子

[发布与删除](qq/forum/threads.md) · [富文本](qq/forum/rich-text.md) · [接收事件](events.md)

每个功能页分别说明参数、返回值和调用示例，完整分类见 [开发文档目录](README.md)
