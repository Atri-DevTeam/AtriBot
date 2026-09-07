const body = (value) => value

/**
 * QQ 机器人 API v2 官方文档中的全部 OpenAPI（2026-09-04）。
 * 路径参数保留为模板，调试页会自动为每个模板生成输入框。
 */
export const officialApiPresets = [
  { category: '接入与机器人', key: 'gateway', label: '获取通用 WSS 接入点', method: 'GET', path: '/gateway' },
  { category: '接入与机器人', key: 'bot-profile', label: '获取机器人详情', method: 'GET', path: '/users/@me' },
  { category: '接入与机器人', key: 'bot-guilds', label: '获取机器人加入的频道列表', method: 'GET', path: '/users/@me/guilds?before=&after=&limit=100' },
  { category: '接入与机器人', key: 'share-link', label: '生成机器人分享链接', method: 'POST', path: '/v2/generate_url_link', body: body({ callback_data: 'atrimeow' }) },

  { category: '群聊消息', key: 'group-message', label: '发送群聊消息', method: 'POST', path: '/v2/groups/{group_openid}/messages', body: body({ msg_type: 0, content: 'hello' }) },
  { category: '群聊消息', key: 'group-file', label: '上传群聊富媒体', method: 'POST', path: '/v2/groups/{group_openid}/files', body: body({ file_type: 1, url: 'https://example.com/image.png', srv_send_msg: false }) },
  { category: '群聊消息', key: 'group-recall', label: '撤回群聊消息', method: 'DELETE', path: '/v2/groups/{group_openid}/messages/{message_id}' },
  { category: '单聊消息', key: 'c2c-message', label: '发送单聊消息', method: 'POST', path: '/v2/users/{user_openid}/messages', body: body({ msg_type: 0, content: 'hello' }) },
  { category: '单聊消息', key: 'c2c-stream-message', label: '发送单聊流式消息', method: 'POST', path: '/v2/users/{user_openid}/stream-messages', body: body({ content: 'hello', stream: { state: 1, id: 'STREAM_ID', index: 0 } }) },
  { category: '单聊消息', key: 'c2c-file', label: '上传单聊富媒体', method: 'POST', path: '/v2/users/{user_openid}/files', body: body({ file_type: 1, url: 'https://example.com/image.png', srv_send_msg: false }) },
  { category: '单聊消息', key: 'c2c-recall', label: '撤回单聊消息', method: 'DELETE', path: '/v2/users/{user_openid}/messages/{message_id}' },

  { category: '分片上传', key: 'group-upload-prepare', label: '群聊文件分片上传初始化', method: 'POST', path: '/v2/groups/{group_id}/upload/prepare', body: body({ file_type: 4, file_name: 'file.zip', file_size: 0 }) },
  { category: '分片上传', key: 'group-upload-finish', label: '群聊文件分片上传完成', method: 'POST', path: '/v2/groups/{group_id}/upload/part/finish', body: body({ upload_id: 'UPLOAD_ID' }) },
  { category: '分片上传', key: 'user-upload-prepare', label: '单聊文件分片上传初始化', method: 'POST', path: '/v2/users/{user_id}/upload/prepare', body: body({ file_type: 4, file_name: 'file.zip', file_size: 0 }) },
  { category: '分片上传', key: 'user-upload-finish', label: '单聊文件分片上传完成', method: 'POST', path: '/v2/users/{user_id}/upload/part/finish', body: body({ upload_id: 'UPLOAD_ID' }) },

  { category: '群聊管理', key: 'group-info', label: '获取群基本信息', method: 'GET', path: '/v2/groups/{group_openid}/info' },
  { category: '群聊管理', key: 'group-bot-state', label: '获取机器人群内状态', method: 'GET', path: '/v2/groups/{group_openid}/bot_state' },
  { category: '群聊管理', key: 'join-request-list', label: '拉取入群申请列表', method: 'GET', path: '/v2/groups/{group_openid}/join_request/list?next_token=' },
  { category: '群聊管理', key: 'approve-join-request', label: '审批入群申请', method: 'POST', path: '/v2/groups/{group_openid}/approval_join_request/{member_openid}', body: body({ action: 1 }) },
  { category: '群聊管理', key: 'restrict-chat-get', label: '查询群成员禁言状态', method: 'GET', path: '/v2/groups/{group_openid}/restrict_chat_setting?member_openid={member_openid}' },
  { category: '群聊管理', key: 'restrict-chat-set', label: '设置群成员禁言', method: 'POST', path: '/v2/groups/{group_openid}/restrict_chat_setting', body: body({ member_openid: '{member_openid}', restrict_seconds: 60 }) },
  { category: '群聊管理', key: 'group-members', label: '获取群成员列表', method: 'GET', path: '/v2/groups/{group_openid}/members?next_token=' },
  { category: '群聊管理', key: 'group-member', label: '获取群成员信息', method: 'GET', path: '/v2/groups/{group_openid}/members/{member_openid}' },
  { category: '群聊管理', key: 'batch-remove-members', label: '批量移除群成员', method: 'POST', path: '/v2/groups/{group_openid}/batch_remove_members', body: body({ member_openids: ['{member_openid}'], add_to_member_blacklist: false }) },
  { category: '群聊管理', key: 'blacklist-get', label: '查询群黑名单', method: 'GET', path: '/v2/groups/{group_openid}/member_blacklist?next_token=' },
  { category: '群聊管理', key: 'blacklist-set', label: '操作群黑名单', method: 'POST', path: '/v2/groups/{group_openid}/member_blacklist', body: body({ member_openids: ['{member_openid}'], action: 1 }) },

  { category: '自动审批', key: 'strategy-list', label: '查询自动审批策略列表', method: 'GET', path: '/v2/groups/join_approval_strategy?next_token=' },
  { category: '自动审批', key: 'strategy-create', label: '创建自动审批策略', method: 'POST', path: '/v2/groups/join_approval_strategy', body: body({ group_openids: ['{group_openid}'], is_enable: 'on', remark: '调试策略' }) },
  { category: '自动审批', key: 'strategy-update', label: '修改自动审批策略', method: 'PATCH', path: '/v2/groups/join_approval_strategy/{strategy_id}', body: body({ is_enable: 'on', remark: '调试策略' }) },
  { category: '自动审批', key: 'strategy-delete', label: '删除自动审批策略', method: 'DELETE', path: '/v2/groups/join_approval_strategy/{strategy_id}' },
  { category: '自动审批', key: 'strategy-execute', label: '执行自动审批策略', method: 'POST', path: '/v2/groups/join_approval_strategy/{strategy_id}/execute', body: body({}) },
  { category: '自动审批', key: 'strategy-whitelist', label: '修改自动审批白名单', method: 'POST', path: '/v2/groups/join_approval_strategy/{strategy_id}/whitelist_users', body: body({ add_user_ids: ['10001'], remove_user_ids: [] }) },

  { category: '菜单与面板', key: 'menu-get', label: '查询全局自定义菜单', method: 'GET', path: '/v2/menu' },
  { category: '菜单与面板', key: 'menu-put', label: '修改全局自定义菜单', method: 'PUT', path: '/v2/menu', body: body({ menu: { items: [{ type: 'send_message', name: '帮助', send_message: '/help' }] } }) },
  { category: '菜单与面板', key: 'panels-list', label: '查询指令面板列表', method: 'GET', path: '/v2/panels?scope=c2c' },
  { category: '菜单与面板', key: 'panels-create', label: '创建指令面板', method: 'POST', path: '/v2/panels', body: body({ scope: 'c2c', target_type: 'all', panel: { items: [{ type: 'command', name: '帮助', desc: '查看帮助' }], remark: '调试面板' } }) },
  { category: '菜单与面板', key: 'panel-get', label: '查询指令面板详情', method: 'GET', path: '/v2/panels/{panel_id}' },
  { category: '菜单与面板', key: 'panel-put', label: '修改指令面板', method: 'PUT', path: '/v2/panels/{panel_id}', body: body({ panel: { items: [{ type: 'command', name: '帮助', desc: '查看帮助' }], remark: '调试面板' } }) },
  { category: '菜单与面板', key: 'panel-delete', label: '删除指令面板', method: 'DELETE', path: '/v2/panels/{panel_id}' },
  { category: '菜单与面板', key: 'panel-target', label: '修改指令面板关联对象', method: 'PUT', path: '/v2/panels/{panel_id}/target', body: body({ add_openids: ['{user_openid}'], remove_openids: [] }) },

  { category: '频道管理', key: 'guild-info', label: '获取频道详情', method: 'GET', path: '/guilds/{guild_id}' },
  { category: '频道管理', key: 'guild-channels', label: '获取子频道列表', method: 'GET', path: '/guilds/{guild_id}/channels' },
  { category: '频道管理', key: 'channel-create', label: '创建子频道', method: 'POST', path: '/guilds/{guild_id}/channels', body: body({ name: '新子频道', type: 0, sub_type: 0, position: 1, private_type: 0 }) },
  { category: '频道管理', key: 'channel-info', label: '获取子频道详情', method: 'GET', path: '/channels/{channel_id}' },
  { category: '频道管理', key: 'channel-update', label: '修改子频道', method: 'PATCH', path: '/channels/{channel_id}', body: body({ name: '新名称' }) },
  { category: '频道管理', key: 'channel-delete', label: '删除子频道', method: 'DELETE', path: '/channels/{channel_id}' },
  { category: '消息交互', key: 'interaction-update', label: '更新互动回调数据', method: 'PUT', path: '/interactions/{interaction_id}', body: body({ code: 0 }) }
]

export const presetCategories = [...new Set(officialApiPresets.map(item => item.category))]
