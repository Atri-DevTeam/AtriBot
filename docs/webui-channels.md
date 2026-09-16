# WebUI 频道页

入口：WebUI 左侧「频道」，地址 `/webui/channels`。使用机器人配置中的 `tencent-channel` EMA 客户端和账号，不使用 QQ 官方机器人频道 API，也不绑定固定用户名。

## 数据边界

- 频道、板块、帖子详情、图片地址、评论、成员均实时查询 EMA。不新增数据库表、内容文件、同步任务、服务端内容缓存或浏览器持久化存储。
- 页面仅在内存中保留当前列表、翻页游标、详情和草稿；切换频道清空旧内容，离开页面取消读取请求。不轮询，不预取每条帖子详情。
- API 和浏览器 fetch 均使用 `no-store`。媒体按 EMA 返回的远程 URL 加载，不由后端下载或保存。
- EMA CLI 自身已有的登录凭据、运行日志机制不在此页面中改动；此处的“不保存”指 AtriMeow 频道业务内容不落库、不另建副本，并非禁用外部 CLI 的所有本机状态。WebUI 调用使用 CLI 的 error 日志级别，应用不记录业务响应正文。

## 已接入

- 已加入/管理/创建的频道列表、频道资料、板块列表。
- 全频道动态、板块帖子、全频道帖子搜索与分页。
- 帖子详情、文字、Markdown 及已有配图展示；评论分页、楼中楼回复自动展开和分页。已加载评论按从上到下顺序串行拉取，一条评论的回复翻完再处理下一条；失败暂停，手动继续。切换/关闭帖子、刷新评论或离开页面时取消旧队列，写操作期间暂停新回复请求。
- 评论及回复使用按用户 ID 固定配色的首字头像，图片支持缩略预览和点击放大。
- 首批回复直接展示 EMA 评论对象的 `replies_preview`；仅在 `has_more_replies` 表示还有回复且该评论含非空 `attach_info` 时进入翻页队列。后续使用回复接口返回的游标，结束或重复游标即停止；不使用评论列表的顶层游标加载回复，后端也拒绝缺少游标的回复查询。
- 发布/编辑文字或 Markdown 长帖、点赞/取消点赞、置顶/取消置顶、精华/取消精华、移动、删除。
- 发表评论、删除自己的评论、以帖主身份删除评论。实际权限由 EMA 校验，管理员身份不等于帖主身份。
- 评论和楼中楼均可点击「回复」，就地输入文字并确认发送。楼中楼使用根评论定位信息以及被回复条目的 `target_reply_id`、`target_user_id`、`target_user_nick`。发送失败保留草稿且不重试，成功后重新读取评论；已有回复展示被回复人。
- 回复人 ID 每次发送前通过 `get-user-info {guild_id}` 获取当前账号在目标频道的昵称，再调用 `guild-member-search {guild_id, keyword, num}`，取昵称完全匹配的唯一成员 `tinyid`。不需要自建频道或管理权限，不硬编码、不缓存、不接受前端指定发送人。成员搜索若有分页会继续读取（最多 5 页），不同 ID 完全重名、搜索未完成或查询失败时明确提示尚未提交，不选择模糊匹配的第一条。CLI 1.0.10 的 `login status` 只用于登录有效性检查，不用于读取用户 ID。
- 成员列表及分页，仅浏览，暂不提供成员踢出/禁言。

本版不提供聊天消息收发或附件上传。编辑不传 CLI 的 `clear_images` / `clear_videos`，不要求清除原帖媒体；Markdown 中已有图片占位符请保留。发布 Markdown 需标题，普通短帖标题可为空。所有发送与管理操作均需用户确认，开发验证不会真实发帖或删除内容。

## 后端

`GET /webui/api/channels/query/{operation}`：`account`、`guilds`、`info`、`boards`、`feeds`、`search`、`detail`、`comments`、`replies`、`share`、`members`。`account` 不传成员 ID，通过 EMA 查询当前登录账号的全局资料，用于顶部账号名称展示。

`POST /webui/api/channels/action/{operation}`：`publish`、`edit`、`like`、`unlike`、`pin`、`unpin`、`essence`、`unessence`、`move`、`delete`、`comment`、`reply`、`delete-comment`、`delete-comment-owner`。

沿用 WebUI 会话鉴权。写请求要求 JSON、`X-Requested-With: XMLHttpRequest` 和 `confirmed: true`，拒绝跨站请求。服务端按操作重建参数白名单，不允许前端指定 CLI 命令、文件路径、ref 或任意原始参数。

频道、板块、帖子、作者 ID 按字符串传递，创建时间沿用 EMA 的 `create_time_raw`，不从格式化日期反推。游标原样转发，单页最多请求 20 条。总计最多接收 4 个页面请求，复用 EMA 全局串行锁，等待锁最多 10 秒。交互调用不自动重试，包括限流与超时；写操作失败时须先刷新核对实际结果，避免重复提交。

未配置/未启用 EMA 返回 503，限流返回 429，超时返回 504，EMA 业务失败返回 502。EMA 账号过期不会注销 WebUI 会话，请在服务器运行 `tencent-channel-cli login`。如果配置了显式 token，应同步更新机器人配置中的 token，CLI 本机扫码登录不会覆盖显式 token。

## 构建

后端：`./gradlew compileJava`。前端：在 `webui` 下执行 `npm run build`，资源输出到 `src/main/resources/official-webui`，再按项目原有方式打包、重启。无需数据库迁移。
