# QQ 官方机器人 API

[文档目录](../README.md) · [机器人与群资料](bot.md)

## QQ 消息

| 功能 | 内容 |
| --- | --- |
| [调用约定](messages/conventions.md) | 来源标识、同步与异步、失败处理 |
| [群消息](messages/group.md) | 主动发送、回复、提及、引用、撤回 |
| [C2C 消息](messages/c2c.md) | 私聊发送、召回消息、正在输入 |
| [频道消息](messages/guild.md) | 文字子频道与频道私信 |
| [流式消息](messages/stream.md) | 文本与 Markdown 增量、结束状态 |
| [图片与文件](messages/media.md) | URL、Base64、本地文件、自动分片 |
| [备用图片上传](messages/backup-upload.md) | 字节数组上传并返回 URL |
| [自定义消息请求](messages/advanced.md) | MessageBody、ChatService、JsonPayload |

## QQ 消息组件

| 功能 | 内容 |
| --- | --- |
| [Markdown](components/markdown.md) | 文字格式、图片、提及、指令标签 |
| [按钮键盘](components/buttons.md) | 布局、样式、权限、确认框 |
| [图文卡片](components/cards.md) | TuWen 内容与发送 |
| [Ark 模板](components/ark.md) | 模板 23、24、37 |
| [频道 Embed](components/embed.md) | 标题、缩略图和字段 |

## QQ 交互配置

| 功能 | 内容 |
| --- | --- |
| [自定义菜单](interaction/menu.md) | 全局菜单、子菜单、开关 |
| [指令面板](interaction/panel.md) | 增删改查、分页、关联对象 |
| [按钮交互](interaction/callback.md) | 接收回调、状态应答、回复消息 |

## QQ 群管理

| 功能 | 内容 |
| --- | --- |
| [禁言](management/mute.md) | 禁言、修改、解除、查询规则 |
| [群成员](management/members.md) | 列表、详情、移除及部分失败 |
| [成员黑名单](management/blacklist.md) | 查询、添加、移除、批量结果 |
| [入群申请](management/join-requests.md) | 申请列表、同意、拒绝 |
| [审核策略](management/join-strategies.md) | 策略、关联群、白名单、执行 |

## QQ 频道帖子

| 功能 | 内容 |
| --- | --- |
| [帖子操作](forum/threads.md) | 发布、删除、帖子与评论数据 |
| [富文本](forum/rich-text.md) | 段落、元素、解析和序列化 |
