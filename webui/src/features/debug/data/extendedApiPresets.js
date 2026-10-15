const channelDocs = 'https://bot.q.qq.com/wiki/develop/api-v2/server-inter/channel/'
const docs = 'https://bot.q.qq.com/wiki/develop/api-v2/'

const api = (category, key, label, method, path, document, body = null, options = {}) => ({
  category, key, label, method, path, body,
  documentation: document.startsWith('https://') ? document : channelDocs + document,
  ...options,
})
const sample = (label, body, options = {}) => ({label, body, ...options})
const guildMessageSamples = [
  sample('被动回复', {content: 'reply', msg_id: 'MESSAGE_ID'}),
  sample('事件回复', {content: 'reply', event_id: 'EVENT_ID'}),
  sample('图片 URL', {image: 'https://example.com/image.png', msg_id: 'MESSAGE_ID'}),
  sample('Markdown', {markdown: {content: '# 消息内容'}, msg_id: 'MESSAGE_ID'}),
  sample('引用消息', {content: 'reply', msg_id: 'MESSAGE_ID', message_reference: {message_id: 'MESSAGE_ID'}}),
  sample('Embed', {embed: {title: '查询结果', prompt: '查询结果', fields: [{name: '示例内容'}]}, msg_id: 'MESSAGE_ID'}),
]
const schedule = {
  name: '活动日程', description: '活动说明', start_timestamp: 'START_TIMESTAMP_MS',
  end_timestamp: 'END_TIMESTAMP_MS', jump_channel_id: '0', remind_type: '0',
}
const permissionNote = 'add 和 remove 为十进制权限位图字符串；1 可见、4 可发言。本接口不支持修改管理子频道权限'
const privateNote = '官方文档标为私域接口'
const roleMemberSamples = [sample('子频道管理员（role_id=5）', {channel: {id: 'CHANNEL_ID'}})]

export const extendedApiPresets = [
  api('接入与机器人', 'access-token', '获取访问凭证', 'POST', '/app/getAppAccessToken', docs + 'dev-prepare/access-token.html',
      {appId: 'APP_ID', clientSecret: 'CLIENT_SECRET'}, {
        note: '请求固定发送到 api.bot.qq.com，不附加机器人 Authorization；appId 与 clientSecret 手动填写，业务错误查看响应 code',
      }),
  api('接入与机器人', 'gateway-shards', '获取带分片 WSS 接入点', 'GET', '/gateway/bot', docs + 'openapi/wss/shard_url_get.html'),

  api('频道消息', 'channel-message', '发送子频道消息', 'POST', '/channels/{channel_id}/messages', 'message/send.html',
      {content: 'hello'}, {samples: guildMessageSamples, note: '频道消息不使用群聊的 msg_type；msg_id 与 event_id 选择其一。图片样例使用 URL'}),
  api('频道消息', 'channel-recall', '撤回子频道消息', 'DELETE', '/channels/{channel_id}/messages/{message_id}?hidetip=false', 'message/recall.html',
      null, {note: `${privateNote}；hidetip=true 隐藏撤回提示`}),
  api('频道私信', 'guild-dm-create', '创建频道私信会话', 'POST', '/users/@me/dms', 'message/dms.html',
      {recipient_id: 'USER_ID', source_guild_id: 'GUILD_ID'}, {note: '机器人与接收者须有共同频道，保存返回的私信 guild_id 用于收发私信'}),
  api('频道私信', 'guild-dm-message', '发送频道私信', 'POST', '/dms/{guild_id}/messages', 'message/dms.html',
      {content: 'hello'}, {samples: guildMessageSamples, note: 'guild_id 为私信会话 ID，取创建会话响应或私信事件中的值，不是源频道 ID'}),
  api('频道私信', 'guild-dm-recall', '撤回频道私信', 'DELETE', '/dms/{guild_id}/messages/{message_id}?hidetip=false', 'message/dms.html',
      null, {note: `${privateNote}；guild_id 为私信会话 ID，只能撤回机器人自己的私信`}),

  api('频道成员', 'guild-members', '获取频道成员列表', 'GET', '/guilds/{guild_id}/members?after=0&limit=100', 'role/member/get_members.html',
      null, {note: `${privateNote}；limit 为 1–400，下一页 after 使用本页最后一个成员的用户 ID，返回空列表时结束`}),
  api('频道成员', 'guild-role-members', '获取频道身份组成员列表', 'GET', '/guilds/{guild_id}/roles/{role_id}/members?start_index=0&limit=100', 'role/member/get_role_members.html',
      null, {note: `${privateNote}；limit 为 1–400，续页 start_index 使用响应 next`}),
  api('频道成员', 'guild-member', '获取频道成员详情', 'GET', '/guilds/{guild_id}/members/{user_id}', 'role/member/get_member.html'),
  api('频道成员', 'guild-member-delete', '删除频道成员', 'DELETE', '/guilds/{guild_id}/members/{user_id}', 'role/member/delete_member.html',
      {add_blacklist: false, delete_history_msg_days: 0}, {note: `${privateNote}；需管理员权限。历史消息撤回范围为 0、3、7、15、30 天或 -1（全部）`}),
  api('频道成员', 'channel-online-count', '获取子频道在线成员数', 'GET', '/channels/{channel_id}/online_nums', 'role/get_online_nums.html',
      null, {note: '适用于音视频或直播子频道'}),

  api('频道身份组', 'guild-roles', '获取频道身份组列表', 'GET', '/guilds/{guild_id}/roles', 'role-group/get_guild_roles.html'),
  api('频道身份组', 'guild-role-create', '创建频道身份组', 'POST', '/guilds/{guild_id}/roles', 'role-group/post_guild_role.html',
      {name: '新身份组', color: 4281558681, hoist: 1}, {note: 'color 为 ARGB 转换后的整数；hoist：0 不单独展示，1 单独展示，需管理员权限'}),
  api('频道身份组', 'guild-role-update', '修改频道身份组', 'PATCH', '/guilds/{guild_id}/roles/{role_id}', 'role-group/patch_guild_role.html',
      {name: '新名称'}, {note: '只提交需要修改的 name、color、hoist，至少填写一项'}),
  api('频道身份组', 'guild-role-delete', '删除频道身份组', 'DELETE', '/guilds/{guild_id}/roles/{role_id}', 'role-group/delete_guild_role.html'),
  api('频道身份组', 'guild-role-member-add', '添加频道身份组成员', 'PUT', '/guilds/{guild_id}/members/{user_id}/roles/{role_id}', 'role-group/put_guild_member_role.html',
      {}, {samples: roleMemberSamples, note: 'role_id=5 时使用子频道管理员样例，并在 Body 中填写 channel.id'}),
  api('频道身份组', 'guild-role-member-remove', '删除频道身份组成员', 'DELETE', '/guilds/{guild_id}/members/{user_id}/roles/{role_id}', 'role-group/delete_guild_member_role.html',
      {}, {samples: roleMemberSamples, note: 'role_id=5 时使用子频道管理员样例，并在 Body 中填写 channel.id'}),

  api('频道权限', 'channel-member-permissions', '获取子频道用户权限', 'GET', '/channels/{channel_id}/members/{user_id}/permissions', 'role-group/channel_permissions/get_channel_permissions.html'),
  api('频道权限', 'channel-member-permissions-put', '修改子频道用户权限', 'PUT', '/channels/{channel_id}/members/{user_id}/permissions', 'role-group/channel_permissions/put_channel_permissions.html',
      {add: '1', remove: '0'}, {note: permissionNote}),
  api('频道权限', 'channel-role-permissions', '获取子频道身份组权限', 'GET', '/channels/{channel_id}/roles/{role_id}/permissions', 'role-group/channel_permissions/get_channel_roles_permissions.html'),
  api('频道权限', 'channel-role-permissions-put', '修改子频道身份组权限', 'PUT', '/channels/{channel_id}/roles/{role_id}/permissions', 'role-group/channel_permissions/put_channel_roles_permissions.html',
      {add: '1', remove: '0'}, {note: permissionNote}),
  api('频道权限', 'guild-api-permissions', '获取机器人可用接口权限', 'GET', '/guilds/{guild_id}/api_permission', 'api_permissions/get_guild_api_permission.html'),
  api('频道权限', 'guild-api-permission-demand', '发送接口授权链接', 'POST', '/guilds/{guild_id}/api_permission/demand', 'api_permissions/post_api_permission_demand.html',
      {channel_id: 'CHANNEL_ID', api_identify: {path: '/guilds/{guild_id}', method: 'GET'}, desc: '获取频道基本信息'},
      {note: 'api_identify.path 保留平台定义的路径模板；channel_id 填写接收授权链接的子频道 ID'}),

  api('频道禁言与设置', 'guild-message-setting', '获取频道消息频率设置', 'GET', '/guilds/{guild_id}/message/setting', 'speak/setting/message_setting.html'),
  api('频道禁言与设置', 'guild-mute', '频道全员／批量成员禁言', 'PATCH', '/guilds/{guild_id}/mute', 'speak/patch_guild_mute.html',
      {mute_seconds: '60'}, {
        samples: [sample('解除全员禁言', {mute_seconds: '0'}),
          sample('批量成员禁言', {mute_seconds: '60', user_ids: ['USER_ID']}, {documentation: channelDocs + 'speak/patch_guild_mute_multi_member.html'}),
          sample('批量解除禁言', {mute_seconds: '0', user_ids: ['USER_ID']}, {documentation: channelDocs + 'speak/patch_guild_mute_multi_member.html'})],
        note: '全员与批量成员共用接口；批量操作必须填写 user_ids。时间为秒数字符串，也可改用 mute_end_timestamp',
      }),
  api('频道禁言与设置', 'guild-member-mute', '频道指定成员禁言', 'PATCH', '/guilds/{guild_id}/members/{user_id}/mute', 'speak/patch_guild_member_mute.html',
      {mute_seconds: '60'}, {samples: [sample('解除禁言', {mute_seconds: '0'})], note: '时间为秒数字符串，也可改用 mute_end_timestamp 指定到期秒时间戳'}),

  api('频道公告与精华', 'guild-announce-create', '创建频道公告', 'POST', '/guilds/{guild_id}/announces', 'content/announces/post_guild_announces.html',
      {channel_id: 'CHANNEL_ID', message_id: 'MESSAGE_ID'}, {
        samples: [sample('推荐子频道欢迎公告', {announces_type: 1, recommend_channels: [{channel_id: 'CHANNEL_ID', introduce: '推荐频道'}]})],
        note: '消息公告和推荐子频道公告互相替换；推荐子频道最多 3 个，且须全体成员可见',
      }),
  api('频道公告与精华', 'guild-announce-delete', '删除频道公告', 'DELETE', '/guilds/{guild_id}/announces/{message_id}', 'content/announces/delete_guild_announces.html',
      null, {note: 'message_id 填 all 可删除当前公告，无需校验消息 ID'}),
  api('频道公告与精华', 'channel-pin-add', '添加精华消息', 'PUT', '/channels/{channel_id}/pins/{message_id}', 'content/pins/put_pins_message.html'),
  api('频道公告与精华', 'channel-pin-delete', '删除精华消息', 'DELETE', '/channels/{channel_id}/pins/{message_id}', 'content/pins/delete_pins_message.html',
      null, {note: 'message_id 填 all 可删除子频道全部精华消息'}),
  api('频道公告与精华', 'channel-pins', '获取精华消息', 'GET', '/channels/{channel_id}/pins', 'content/pins/get_pins_message.html'),

  api('频道日程', 'channel-schedules', '获取频道日程列表', 'GET', '/channels/{channel_id}/schedules', 'content/schedule/get_schedules.html',
      null, {note: '默认返回当天日程；可在 Params 添加 since（毫秒时间戳）指定起始时间'}),
  api('频道日程', 'channel-schedule', '获取日程详情', 'GET', '/channels/{channel_id}/schedules/{schedule_id}', 'content/schedule/get_schedule.html'),
  api('频道日程', 'channel-schedule-create', '创建日程', 'POST', '/channels/{channel_id}/schedules', 'content/schedule/post_schedule.html',
      {schedule}, {note: '开始和结束时间填写毫秒时间戳字符串；remind_type 为 0–5 的字符串，0 不提醒，需管理员权限'}),
  api('频道日程', 'channel-schedule-update', '修改日程', 'PATCH', '/channels/{channel_id}/schedules/{schedule_id}', 'content/schedule/patch_schedule.html',
      {schedule}, {note: 'schedule 无需携带 id，开始和结束时间填写毫秒时间戳字符串'}),
  api('频道日程', 'channel-schedule-delete', '删除日程', 'DELETE', '/channels/{channel_id}/schedules/{schedule_id}', 'content/schedule/delete_schedule.html'),

  api('频道音频', 'channel-audio', '音频控制', 'POST', '/channels/{channel_id}/audio', 'content/audio/audio_control.html',
      {audio_url: 'https://example.com/audio.mp3', text: '音频标题', status: 0}, {
        samples: [sample('暂停', {status: 1}), sample('继续', {status: 2}), sample('停止', {status: 3})],
        note: '需要音频类机器人接口权限；播放状态：0 开始、1 暂停、2 继续、3 停止',
      }),
  api('频道音频', 'channel-mic-up', '机器人上麦', 'PUT', '/channels/{channel_id}/mic', 'content/audio/put_mic.html', {}, {note: '需要音频类机器人接口权限'}),
  api('频道音频', 'channel-mic-down', '机器人下麦', 'DELETE', '/channels/{channel_id}/mic', 'content/audio/delete_mic.html', null, {note: '需要音频类机器人接口权限'}),

  api('频道帖子', 'channel-threads', '获取帖子列表', 'GET', '/channels/{channel_id}/threads', 'content/forum/get_threads_list.html', null, {note: privateNote}),
  api('频道帖子', 'channel-thread', '获取帖子详情', 'GET', '/channels/{channel_id}/threads/{thread_id}', 'content/forum/get_thread.html', null, {note: privateNote}),
  api('频道帖子', 'channel-thread-create', '发表帖子', 'PUT', '/channels/{channel_id}/threads', 'content/forum/put_thread.html',
      {title: '帖子标题', content: '帖子正文', format: 1}, {
        samples: [sample('Markdown', {title: '帖子标题', content: '# 正文', format: 3}),
          sample('HTML', {title: '帖子标题', content: '<p>帖子正文</p>', format: 2}),
          sample('RichText', {title: '帖子标题', content: JSON.stringify({paragraphs: [{elems: [{type: 1, text: {text: '帖子正文'}}], props: {}}]}), format: 4})],
        note: `${privateNote}；format=4 时 content 是 RichText 的 JSON 字符串。响应 task_id 不是用于删除的 thread_id`,
      }),
  api('频道帖子', 'channel-thread-delete', '删除帖子', 'DELETE', '/channels/{channel_id}/threads/{thread_id}', 'content/forum/delete_thread.html', null, {note: privateNote}),

  api('频道表情表态', 'channel-reaction-add', '添加表情表态', 'PUT', '/channels/{channel_id}/messages/{message_id}/reactions/{type}/{id}', docs + 'server-inter/message/trans/emoji.html'),
  api('频道表情表态', 'channel-reaction-delete', '删除自己的表情表态', 'DELETE', '/channels/{channel_id}/messages/{message_id}/reactions/{type}/{id}', docs + 'server-inter/message/trans/emoji.html'),
  api('频道表情表态', 'channel-reaction-users', '获取表情表态用户列表', 'GET', '/channels/{channel_id}/messages/{message_id}/reactions/{type}/{id}?cookie=&limit=20', docs + 'server-inter/message/trans/emoji.html',
      null, {note: '首次 cookie 留空；续页使用返回的 cookie。limit 最大 50，仅首次请求设置'}),

  api('已废弃接口', 'channel-announce-create-legacy', '创建子频道公告（已废弃）', 'POST', '/channels/{channel_id}/announces', 'content/announces/post_channel_announces.html',
      {message_id: 'MESSAGE_ID'}, {deprecated: true, note: '官方已废弃，替代接口为添加精华消息'}),
  api('已废弃接口', 'channel-announce-delete-legacy', '删除子频道公告（已废弃）', 'DELETE', '/channels/{channel_id}/announces/{message_id}', 'content/announces/delete_channel_announces.html',
      null, {deprecated: true, note: '官方已废弃，替代接口为删除精华消息'}),
]
