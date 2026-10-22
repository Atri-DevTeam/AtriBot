# 开发文档

[atri-core](../README.md)

本文档按当前 core 的公开调用入口组织，描述本地封装、参数和结果处理。平台资格与额度以实际接口响应为准

::: tip 阅读顺序
首次接入从 [快速开始](getting-started.md) 阅读，再按使用场景查阅下方 API 分类。编写 QQ 消息功能前，建议先了解 [消息调用约定](qq/messages/conventions.md) 中的来源标识与结果处理
:::

## 框架接入

| 文档 | 内容 |
| --- | --- |
| [快速开始](getting-started.md) | 依赖、配置、完整启动入口 |
| [配置](configuration.md) | atri-core.yml 与重载 |
| [Provider](provider.md) | 服务生命周期、权限与日志扩展 |
| [指令](commands.md) | 声明、注册与分发 |
| [事件](events.md) | 事件注册、各事件字段和操作 |
| [用户与消息](messages.md) | 平台对象、会话标识与功能入口 |
| [任务调度](scheduling.md) | 计划任务、延迟任务、共享执行器 |
| [构建与开发](development.md) | 本地联调、测试和 JavaDoc |

## QQ 消息

| 功能 | 内容 |
| --- | --- |
| [调用约定](qq/messages/conventions.md) | 来源标识、同步与异步、失败处理 |
| [群消息](qq/messages/group.md) | 主动发送、回复、提及、引用、撤回 |
| [C2C 消息](qq/messages/c2c.md) | 私聊发送、召回消息、正在输入 |
| [频道消息](qq/messages/guild.md) | 文字子频道与频道私信 |
| [流式消息](qq/messages/stream.md) | 文本与 Markdown 增量、结束状态 |
| [图片与文件](qq/messages/media.md) | URL、Base64、本地文件、自动分片 |
| [Markdown 图片素材](qq/messages/materials.md) | 素材上传、分页查询、删除与引用 |
| [备用图片上传](qq/messages/backup-upload.md) | 字节数组上传并返回 URL |
| [自定义消息请求](qq/messages/advanced.md) | MessageBody、ChatService、JsonPayload |

## QQ 消息组件

| 功能 | 内容 |
| --- | --- |
| [Markdown](qq/components/markdown.md) | 文字格式、图片、提及、指令标签 |
| [按钮键盘](qq/components/buttons.md) | 布局、样式、权限、确认框 |
| [图文卡片](qq/components/cards.md) | TuWen 内容与发送 |
| [Ark 模板](qq/components/ark.md) | 模板 23、24、37，被动消息需私域机器人 |
| [频道 Embed](qq/components/embed.md) | 标题、缩略图和字段 |

## QQ 交互配置

| 功能 | 内容 |
| --- | --- |
| [自定义菜单](qq/interaction/menu.md) | 全局菜单、子菜单、开关 |
| [指令面板](qq/interaction/panel.md) | 增删改查、分页、关联对象 |
| [按钮交互](qq/interaction/callback.md) | 接收回调、状态应答、回复消息 |

## QQ 群管理

::: important 群管理授权
群禁言、加群审批和群踢人需要开放平台和群主同时授权才可使用
:::

| 功能 | 内容 |
| --- | --- |
| [禁言](qq/management/mute.md) | 禁言、修改、解除、查询规则 |
| [群成员](qq/management/members.md) | 列表、详情、移除及部分失败 |
| [成员黑名单](qq/management/blacklist.md) | 查询、添加、移除、批量结果 |
| [入群申请](qq/management/join-requests.md) | 申请列表、同意、拒绝 |
| [审核策略](qq/management/join-strategies.md) | 策略、关联群、白名单、执行 |

## QQ 频道帖子

| 功能 | 内容 |
| --- | --- |
| [帖子操作](qq/forum/threads.md) | 发布、删除、帖子与评论数据 |
| [富文本](qq/forum/rich-text.md) | 段落、元素、解析和序列化 |

## Napcat / OneBot

| 功能 | 内容 |
| --- | --- |
| [消息](napcat/messages.md) | 群聊、私聊、引用、撤回 |
| [消息段](napcat/segments.md) | 结构、CQ 解析、底层组合方法 |
| [合并转发](napcat/forwarding.md) | 节点、作者、标题和摘要 |
| [群操作](napcat/groups.md) | 资料、申请、戳一戳、表情、签到 |
| [用户与好友](napcat/users.md) | 资料、好友缓存、申请和名片 |
| [HTTP 调用](napcat/requests.md) | 请求入口、响应判断、请求类型目录 |

## QQ 机器人资料

[机器人与群资料](qq/bot.md)：机器人信息、群资料、机器人在群内的状态与错误判断
