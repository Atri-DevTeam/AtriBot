import {extendedApiPresets} from './extendedApiPresets.js'

export const officialApiDocsUrl = 'https://bot.q.qq.com/wiki/develop/api-v2/autogen/'

// 按官方接口索引逐页核对，更新日期：2026-10-13
const autogenApiPresets = [
  {
    "category": "接入与机器人",
    "key": "gateway",
    "label": "获取通用 WSS 接入点",
    "method": "GET",
    "path": "/gateway",
    "doc": "gateway.get.html"
  },
  {
    "category": "接入与机器人",
    "key": "bot-profile",
    "label": "获取机器人详情",
    "method": "GET",
    "path": "/users/@me",
    "doc": "users_me.get.html"
  },
  {
    "category": "接入与机器人",
    "key": "bot-guilds",
    "label": "获取机器人频道列表",
    "method": "GET",
    "path": "/users/@me/guilds?limit=100",
    "note": "分页可添加 before 或 after，两者同时传入时 after 不生效",
    "doc": "users_me_guilds.get.html"
  },
  {
    "category": "接入与机器人",
    "key": "share-link",
    "label": "生成分享链接",
    "method": "POST",
    "path": "/v2/generate_url_link",
    "body": {
      "callback_data": "atrimeow"
    },
    "doc": "v2_generate_url_link.post.html"
  },
  {
    "category": "群聊消息",
    "key": "group-message",
    "label": "发送群聊消息",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/messages",
    "body": {
      "msg_type": 0,
      "content": "hello"
    },
    "samples": [
      {
        "label": "引用消息",
        "body": {
          "msg_type": 0,
          "content": "reply",
          "msg_id": "MESSAGE_ID",
          "msg_seq": 1,
          "message_reference": {
            "message_id": "REFIDX_VALUE"
          }
        }
      }
    ],
    "note": "群消息不支持流式参数；引用使用 ref_idx，被动回复使用事件 d.id",
    "doc": "v2_groups_group_openid_messages.post.html"
  },
  {
    "category": "群聊消息",
    "key": "group-file",
    "label": "群聊富媒体上传",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/files",
    "body": {
      "file_type": 1,
      "url": "https://example.com/image.png",
      "srv_send_msg": false
    },
    "samples": [
      {
        "label": "二进制数据上传",
        "body": {
          "file_type": 1,
          "file_name": "image.png",
          "file_data": "FILE_DATA",
          "srv_send_msg": false
        }
      },
      {
        "label": "分片合并",
        "body": {
          "file_type": 4,
          "file_name": "file.zip",
          "upload_id": "UPLOAD_ID",
          "srv_send_msg": false
        }
      },
      {
        "label": "URL 上传文件",
        "body": {
          "file_type": 4,
          "url": "https://example.com/file.zip",
          "srv_send_msg": false
        }
      }
    ],
    "note": "file_type：1 图片、2 视频、3 语音、4 文件；二进制直传不超过 5M，直传及分片合并需填写 file_name",
    "doc": "v2_groups_group_openid_files.post.html"
  },
  {
    "category": "群聊消息",
    "key": "group-recall",
    "label": "撤回群聊消息",
    "method": "DELETE",
    "path": "/v2/groups/{group_openid}/messages/{message_id}",
    "doc": "v2_groups_group_openid_messages_message_id.delete.html"
  },
  {
    "category": "单聊消息",
    "key": "c2c-message",
    "label": "发送单聊消息",
    "method": "POST",
    "path": "/v2/users/{user_openid}/messages",
    "body": {
      "msg_type": 0,
      "content": "hello"
    },
    "samples": [
      {
        "label": "输入中状态",
        "body": {
          "msg_type": 6,
          "input_notify": {
            "input_type": 1,
            "input_second": 60
          },
          "msg_id": "MESSAGE_ID",
          "msg_seq": 1
        }
      },
      {
        "label": "互动召回",
        "body": {
          "msg_type": 0,
          "content": "hello",
          "is_wakeup": true
        }
      },
      {
        "label": "引用消息",
        "body": {
          "msg_type": 0,
          "content": "reply",
          "msg_id": "MESSAGE_ID",
          "msg_seq": 1,
          "message_reference": {
            "message_id": "REFIDX_VALUE"
          }
        }
      }
    ],
    "note": "msg_id 与 event_id 二选一；is_wakeup 与两者互斥。引用使用 ref_idx，不是被动回复 msg_id",
    "doc": "v2_users_user_openid_messages.post.html"
  },
  {
    "category": "单聊消息",
    "key": "c2c-stream-message",
    "label": "流式发送单聊消息",
    "method": "POST",
    "path": "/v2/users/{user_openid}/stream_messages",
    "body": {
      "input_mode": "append",
      "input_state": 1,
      "index": 0,
      "content_type": "markdown",
      "content_raw": "开始生成回答",
      "msg_id": "MESSAGE_ID",
      "msg_seq": 1
    },
    "samples": [
      {
        "label": "续片",
        "body": {
          "input_mode": "append",
          "input_state": 1,
          "index": 1,
          "content_type": "markdown",
          "content_raw": "后续内容",
          "msg_id": "MESSAGE_ID",
          "stream_msg_id": "STREAM_MSG_ID",
          "msg_seq": 1
        }
      },
      {
        "label": "结束片",
        "body": {
          "input_mode": "append",
          "input_state": 10,
          "index": 2,
          "content_type": "markdown",
          "content_raw": "回答结束",
          "msg_id": "MESSAGE_ID",
          "stream_msg_id": "STREAM_MSG_ID",
          "msg_seq": 1
        }
      },
      {
        "label": "替换全文",
        "body": {
          "input_mode": "replace",
          "input_state": 10,
          "index": 1,
          "content_type": "text",
          "content_raw": "已发送的前缀及后续完整内容",
          "msg_id": "MESSAGE_ID",
          "stream_msg_id": "STREAM_MSG_ID",
          "msg_seq": 1
        }
      }
    ],
    "note": "首片不填 stream_msg_id；续片携带返回的 ID，index 递增，input_state=10 结束；replace 的正文须保留已发送前缀",
    "doc": "v2_users_user_openid_stream_messages.post.html"
  },
  {
    "category": "单聊消息",
    "key": "c2c-file",
    "label": "单聊富媒体上传",
    "method": "POST",
    "path": "/v2/users/{user_openid}/files",
    "body": {
      "file_type": 1,
      "url": "https://example.com/image.png",
      "srv_send_msg": false
    },
    "samples": [
      {
        "label": "二进制数据上传",
        "body": {
          "file_type": 1,
          "file_name": "image.png",
          "file_data": "FILE_DATA",
          "srv_send_msg": false
        }
      },
      {
        "label": "分片合并",
        "body": {
          "file_type": 4,
          "file_name": "file.zip",
          "upload_id": "UPLOAD_ID",
          "srv_send_msg": false
        }
      },
      {
        "label": "URL 上传文件",
        "body": {
          "file_type": 4,
          "url": "https://example.com/file.zip",
          "srv_send_msg": false
        }
      }
    ],
    "note": "file_type：1 图片、2 视频、3 语音、4 文件；二进制直传不超过 5M，直传及分片合并需填写 file_name",
    "doc": "v2_users_user_openid_files.post.html"
  },
  {
    "category": "单聊消息",
    "key": "c2c-recall",
    "label": "撤回单聊消息",
    "method": "DELETE",
    "path": "/v2/users/{user_openid}/messages/{message_id}",
    "doc": "v2_users_user_openid_messages_message_id.delete.html"
  },
  {
    "category": "分片上传",
    "key": "group-upload-prepare",
    "label": "群聊富媒体预上传",
    "method": "POST",
    "path": "/v2/groups/{group_id}/upload_prepare",
    "body": {
      "file_type": 4,
      "file_name": "file.zip",
      "file_size": "FILE_SIZE_BYTES",
      "md5": "FILE_MD5",
      "sha1": "FILE_SHA1",
      "md5_10m": "FIRST_10002432_BYTES_MD5"
    },
    "note": "file_size 为字符串；校验值按实际文件填写，md5_10m 对应前 10002432 字节。分片数据需另行 PUT 到返回的预签名 URL",
    "doc": "v2_groups_group_id_upload_prepare.post.html"
  },
  {
    "category": "分片上传",
    "key": "group-upload-finish",
    "label": "群聊分片上传完成",
    "method": "POST",
    "path": "/v2/groups/{group_id}/upload_part_finish",
    "body": {
      "upload_id": "UPLOAD_ID",
      "part_index": 0,
      "block_size": "PART_SIZE_BYTES",
      "md5": "PART_MD5"
    },
    "note": "每个分片上传成功后调用；全部完成后使用 /files 的 upload_id 样例合并",
    "doc": "v2_groups_group_id_upload_part_finish.post.html"
  },
  {
    "category": "分片上传",
    "key": "user-upload-prepare",
    "label": "单聊富媒体预上传",
    "method": "POST",
    "path": "/v2/users/{user_id}/upload_prepare",
    "body": {
      "file_type": 4,
      "file_name": "file.zip",
      "file_size": "FILE_SIZE_BYTES",
      "md5": "FILE_MD5",
      "sha1": "FILE_SHA1",
      "md5_10m": "FIRST_10002432_BYTES_MD5"
    },
    "note": "file_size 为字符串；校验值按实际文件填写，md5_10m 对应前 10002432 字节。分片数据需另行 PUT 到返回的预签名 URL",
    "doc": "v2_users_user_id_upload_prepare.post.html"
  },
  {
    "category": "分片上传",
    "key": "user-upload-finish",
    "label": "单聊分片上传完成",
    "method": "POST",
    "path": "/v2/users/{user_id}/upload_part_finish",
    "body": {
      "upload_id": "UPLOAD_ID",
      "part_index": 0,
      "block_size": "PART_SIZE_BYTES",
      "md5": "PART_MD5"
    },
    "note": "每个分片上传成功后调用；全部完成后使用 /files 的 upload_id 样例合并",
    "doc": "v2_users_user_id_upload_part_finish.post.html"
  },
  {
    "category": "群聊管理",
    "key": "group-info",
    "label": "获取群基本信息",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/info",
    "doc": "v2_groups_group_openid_info.get.html"
  },
  {
    "category": "群聊管理",
    "key": "group-bot-state",
    "label": "获取机器人群内状态",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/bot_state",
    "doc": "v2_groups_group_openid_bot_state.get.html"
  },
  {
    "category": "群聊管理",
    "key": "join-request-list",
    "label": "入群申请列表拉取",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/join_request_list?cursor=&limit=20",
    "note": "需群管理员身份；limit 最大 50，后续页填写返回的 cursor",
    "doc": "v2_groups_group_openid_join_request_list.get.html"
  },
  {
    "category": "群聊管理",
    "key": "approve-join-request",
    "label": "入群申请审批",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/approval_join_request/{member_openid}",
    "body": {
      "op": "approve",
      "join_request_id": "JOIN_REQUEST_ID"
    },
    "samples": [
      {
        "label": "拒绝申请",
        "body": {
          "op": "decline",
          "join_request_id": "JOIN_REQUEST_ID",
          "reject_reason": "申请未通过",
          "add_to_member_blacklist": false
        }
      }
    ],
    "note": "需群管理员身份；申请 ID 从入群申请列表取得",
    "doc": "v2_groups_group_openid_approval_join_request_member_openid.post.html"
  },
  {
    "category": "群聊管理",
    "key": "restrict-chat-get",
    "label": "查询群禁言状态",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/restrict_chat_setting",
    "note": "查询全员禁言模式及成员禁言列表，需群管理员身份",
    "doc": "v2_groups_group_openid_restrict_chat_setting.get.html"
  },
  {
    "category": "群聊管理",
    "key": "restrict-chat-set",
    "label": "设置群成员禁言",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/restrict_chat_setting",
    "body": {
      "members": [
        {
          "op": "add",
          "member_openid": "MEMBER_OPENID",
          "mute_expire_at": "RFC3339_TIME"
        }
      ]
    },
    "samples": [
      {
        "label": "修改到期时间",
        "body": {
          "members": [
            {
              "op": "update",
              "member_openid": "MEMBER_OPENID",
              "mute_expire_at": "RFC3339_TIME"
            }
          ]
        }
      },
      {
        "label": "解除禁言",
        "body": {
          "members": [
            {
              "op": "del",
              "member_openid": "MEMBER_OPENID",
              "mute_expire_at": ""
            }
          ]
        }
      }
    ],
    "note": "到期时间填写 RFC3339，例如 2026-10-14T08:00:00+08:00；最长 30 天，每批最多 20 人，需群管理员身份",
    "doc": "v2_groups_group_openid_restrict_chat_setting.post.html"
  },
  {
    "category": "群聊管理",
    "key": "group-members",
    "label": "获取群成员列表",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/members?cursor=",
    "note": "当前文档标为内邀接口；每页最多 30 条，续页使用 next_cursor",
    "doc": "v2_groups_group_openid_members.get.html"
  },
  {
    "category": "群聊管理",
    "key": "group-member",
    "label": "获取群成员信息",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/members/{member_openid}",
    "note": "当前文档标为内邀接口；member_openid 为群成员 OpenID",
    "doc": "v2_groups_group_openid_members_member_openid.get.html"
  },
  {
    "category": "群聊管理",
    "key": "batch-remove-members",
    "label": "群成员批量移除",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/batch_remove_members",
    "body": {
      "member_openids": [
        "MEMBER_OPENID"
      ],
      "add_to_member_blacklist": false
    },
    "note": "当前文档标为内邀接口；单次最多 20 个成员",
    "doc": "v2_groups_group_openid_batch_remove_members.post.html"
  },
  {
    "category": "群聊管理",
    "key": "blacklist-get",
    "label": "群黑名单查询",
    "method": "GET",
    "path": "/v2/groups/{group_openid}/member_blacklist?cursor=&limit=20",
    "note": "当前文档标为内邀接口；limit 最大 100",
    "doc": "v2_groups_group_openid_member_blacklist.get.html"
  },
  {
    "category": "群聊管理",
    "key": "blacklist-set",
    "label": "群黑名单操作",
    "method": "POST",
    "path": "/v2/groups/{group_openid}/member_blacklist",
    "body": {
      "op": "add",
      "member_openids": [
        "MEMBER_OPENID"
      ]
    },
    "samples": [
      {
        "label": "移出黑名单",
        "body": {
          "op": "del",
          "member_openids": [
            "MEMBER_OPENID"
          ]
        }
      }
    ],
    "note": "当前文档标为内邀接口；单次最多 20 人，仍在群中的成员不能加入黑名单",
    "doc": "v2_groups_group_openid_member_blacklist.post.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-list",
    "label": "查询入群自动审批策略列表",
    "method": "GET",
    "path": "/v2/groups/join_approval_strategy?cursor=&limit=20",
    "note": "limit 最大 50，后续页填写返回的 cursor",
    "doc": "v2_groups_join_approval_strategy.get.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-create",
    "label": "创建入群自动审批策略",
    "method": "POST",
    "path": "/v2/groups/join_approval_strategy",
    "body": {
      "group_openids": [
        "GROUP_OPENID"
      ],
      "is_enable": "on",
      "remark": "调试策略"
    },
    "samples": [
      {
        "label": "按 QQ 群号关联",
        "body": {
          "group_ids": [
            123456789
          ],
          "is_enable": "on",
          "remark": "调试策略"
        }
      }
    ],
    "note": "group_openids 与 group_ids 二选一，每次最多 100 个群；可添加 expire_at 指定 RFC3339 过期时间",
    "doc": "v2_groups_join_approval_strategy.post.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-update",
    "label": "修改入群自动审批策略",
    "method": "PATCH",
    "path": "/v2/groups/join_approval_strategy/{strategy_id}",
    "body": {
      "is_enable": "on",
      "remark": "调试策略"
    },
    "samples": [
      {
        "label": "添加关联群",
        "body": {
          "group_action": {
            "op": "add",
            "group_openids": [
              "GROUP_OPENID"
            ]
          }
        }
      },
      {
        "label": "移除关联群",
        "body": {
          "group_action": {
            "op": "del",
            "group_openids": [
              "GROUP_OPENID"
            ]
          }
        }
      },
      {
        "label": "停用策略",
        "body": {
          "is_enable": "off"
        }
      },
      {
        "label": "修改过期时间",
        "body": {
          "expire_at": "RFC3339_TIME"
        }
      }
    ],
    "note": "关联群使用的标识类型须与创建时一致",
    "doc": "v2_groups_join_approval_strategy_strategy_id.patch.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-delete",
    "label": "删除入群自动审批策略",
    "method": "DELETE",
    "path": "/v2/groups/join_approval_strategy/{strategy_id}",
    "doc": "v2_groups_join_approval_strategy_strategy_id.delete.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-execute",
    "label": "执行入群自动审批策略",
    "method": "POST",
    "path": "/v2/groups/join_approval_strategy/{strategy_id}/execute",
    "body": {},
    "note": "扫描策略关联群的申请并按白名单审批，异步执行，文档预计约 10 分钟完成",
    "doc": "v2_groups_join_approval_strategy_strategy_id_execute.post.html"
  },
  {
    "category": "自动审批",
    "key": "strategy-whitelist",
    "label": "修改入群自动审批策略的白名单号码",
    "method": "POST",
    "path": "/v2/groups/join_approval_strategy/{strategy_id}/whitelist_users",
    "body": {
      "op": "add",
      "whitelist_users": [
        "QQ_NUMBER"
      ]
    },
    "samples": [
      {
        "label": "删除白名单号码",
        "body": {
          "op": "del",
          "whitelist_users": [
            "QQ_NUMBER"
          ]
        }
      }
    ],
    "note": "填写 QQ 号码字符串，每次最多 10000 个，策略总上限 100000 个",
    "doc": "v2_groups_join_approval_strategy_strategy_id_whitelist_users.post.html"
  },
  {
    "category": "菜单与面板",
    "key": "menu-get",
    "label": "查询全局自定义菜单",
    "method": "GET",
    "path": "/v2/menu",
    "doc": "v2_menu.get.html"
  },
  {
    "category": "菜单与面板",
    "key": "menu-put",
    "label": "修改全局自定义菜单",
    "method": "PUT",
    "path": "/v2/menu",
    "body": {
      "menu": {
        "items": [
          {
            "type": "send_message",
            "name": "帮助",
            "send_message": "/help"
          }
        ]
      }
    },
    "samples": [
      {
        "label": "链接菜单",
        "body": {
          "menu": {
            "items": [
              {
                "type": "link",
                "name": "网站",
                "link": "https://example.com"
              }
            ]
          }
        }
      },
      {
        "label": "折叠菜单",
        "body": {
          "menu": {
            "items": [
              {
                "type": "menu",
                "name": "更多",
                "sub_menu_items": [
                  {
                    "type": "send_message",
                    "name": "帮助",
                    "send_message": "/help"
                  }
                ]
              }
            ]
          }
        }
      },
      {
        "label": "开关菜单",
        "body": {
          "menu": {
            "items": [
              {
                "type": "switch",
                "name": "搜索",
                "switch": {
                  "switch_id": "search",
                  "default": false
                }
              }
            ]
          }
        }
      }
    ],
    "note": "仅单聊生效；提交后覆盖完整菜单，一级最多 10 项，二级最多 5 项",
    "doc": "v2_menu.put.html"
  },
  {
    "category": "菜单与面板",
    "key": "panels-list",
    "label": "查询指令面板列表",
    "method": "GET",
    "path": "/v2/panels?scope=c2c&cursor=&limit=20",
    "note": "scope 必填：c2c / group / channel / dm；limit 最大 50",
    "doc": "v2_panels.get.html"
  },
  {
    "category": "菜单与面板",
    "key": "panels-create",
    "label": "创建指令面板",
    "method": "POST",
    "path": "/v2/panels",
    "body": {
      "scope": "c2c",
      "target_type": "all",
      "panel": {
        "items": [
          {
            "type": "command",
            "name": "帮助",
            "desc": "查看帮助",
            "only_admin": false
          }
        ],
        "remark": "调试面板"
      }
    },
    "samples": [
      {
        "label": "指定群",
        "body": {
          "scope": "group",
          "target_type": "specific",
          "group_openids": [
            "GROUP_OPENID"
          ],
          "panel": {
            "items": [
              {
                "type": "command",
                "name": "帮助",
                "desc": "查看帮助",
                "only_admin": false
              }
            ],
            "remark": "调试面板"
          }
        }
      },
      {
        "label": "指定用户",
        "body": {
          "scope": "c2c",
          "target_type": "specific",
          "user_openids": [
            "USER_OPENID"
          ],
          "panel": {
            "items": [
              {
                "type": "command",
                "name": "帮助",
                "desc": "查看帮助",
                "only_admin": false
              }
            ],
            "remark": "调试面板"
          }
        }
      },
      {
        "label": "频道全局",
        "body": {
          "scope": "channel",
          "target_type": "all",
          "panel": {
            "items": [
              {
                "type": "command",
                "name": "帮助",
                "desc": "查看帮助",
                "only_admin": false
              }
            ],
            "remark": "调试面板"
          }
        }
      },
      {
        "label": "频道私信全局",
        "body": {
          "scope": "dm",
          "target_type": "all",
          "panel": {
            "items": [
              {
                "type": "command",
                "name": "帮助",
                "desc": "查看帮助",
                "only_admin": false
              }
            ],
            "remark": "调试面板"
          }
        }
      }
    ],
    "note": "channel / dm 仅支持全局面板；每个机器人最多 20 个面板，每个面板最多 20 项",
    "doc": "v2_panels.post.html"
  },
  {
    "category": "菜单与面板",
    "key": "panel-get",
    "label": "查询指令面板详情",
    "method": "GET",
    "path": "/v2/panels/{panel_id}",
    "doc": "v2_panels_panel_id.get.html"
  },
  {
    "category": "菜单与面板",
    "key": "panel-put",
    "label": "修改指令面板",
    "method": "PUT",
    "path": "/v2/panels/{panel_id}",
    "body": {
      "panel": {
        "items": [
          {
            "type": "command",
            "name": "帮助",
            "desc": "查看帮助"
          }
        ],
        "remark": "调试面板"
      }
    },
    "note": "覆盖面板元素与备注，不改变已关联的用户或群",
    "doc": "v2_panels_panel_id.put.html"
  },
  {
    "category": "菜单与面板",
    "key": "panel-delete",
    "label": "删除指令面板",
    "method": "DELETE",
    "path": "/v2/panels/{panel_id}",
    "doc": "v2_panels_panel_id.delete.html"
  },
  {
    "category": "菜单与面板",
    "key": "panel-target",
    "label": "修改指令面板关联对象",
    "method": "PUT",
    "path": "/v2/panels/{panel_id}/target",
    "body": {
      "op": "add",
      "user_openids": [
        "USER_OPENID"
      ]
    },
    "samples": [
      {
        "label": "移除用户",
        "body": {
          "op": "del",
          "user_openids": [
            "USER_OPENID"
          ]
        }
      },
      {
        "label": "添加群",
        "body": {
          "op": "add",
          "group_openids": [
            "GROUP_OPENID"
          ]
        }
      },
      {
        "label": "移除群",
        "body": {
          "op": "del",
          "group_openids": [
            "GROUP_OPENID"
          ]
        }
      }
    ],
    "note": "仅 c2c / group 场景支持；按场景填写对应列表，每次最多 20 个",
    "doc": "v2_panels_panel_id_target.put.html"
  },
  {
    "category": "频道管理",
    "key": "guild-info",
    "label": "获取频道详情",
    "method": "GET",
    "path": "/guilds/{guild_id}",
    "doc": "guilds_guild_id.get.html"
  },
  {
    "category": "频道管理",
    "key": "guild-channels",
    "label": "获取子频道列表",
    "method": "GET",
    "path": "/guilds/{guild_id}/channels",
    "doc": "guilds_guild_id_channels.get.html"
  },
  {
    "category": "频道管理",
    "key": "channel-create",
    "label": "创建子频道",
    "method": "POST",
    "path": "/guilds/{guild_id}/channels",
    "body": {
      "name": "新子频道",
      "type": 0,
      "sub_type": 1,
      "position": 3,
      "parent_id": "0",
      "private_type": 0,
      "speak_permission": 1
    },
    "samples": [
      {
        "label": "语音子频道",
        "body": {
          "name": "语音频道",
          "type": 2,
          "sub_type": 3,
          "position": 4,
          "parent_id": "0"
        }
      }
    ],
    "note": "私域接口，需管理员权限；可按需添加 private_user_ids 和 application_id",
    "doc": "guilds_guild_id_channels.post.html"
  },
  {
    "category": "频道管理",
    "key": "channel-info",
    "label": "获取子频道详情",
    "method": "GET",
    "path": "/channels/{channel_id}",
    "doc": "channels_channel_id.get.html"
  },
  {
    "category": "频道管理",
    "key": "channel-update",
    "label": "修改子频道",
    "method": "PATCH",
    "path": "/channels/{channel_id}",
    "body": {
      "name": "新名称",
      "position": 3,
      "parent_id": "0"
    },
    "note": "私域接口，需管理员权限；仅填写要修改的字段，可设置 private_type 与 speak_permission",
    "doc": "channels_channel_id.patch.html"
  },
  {
    "category": "频道管理",
    "key": "channel-delete",
    "label": "删除子频道",
    "method": "DELETE",
    "path": "/channels/{channel_id}",
    "note": "私域接口，需管理员权限",
    "doc": "channels_channel_id.delete.html"
  },
  {
    "category": "消息交互",
    "key": "interaction-update",
    "label": "互动事件响应",
    "method": "PUT",
    "path": "/interactions/{interaction_id}",
    "body": {
      "code": 0
    },
    "note": "interaction_id 取事件 d.id，不带事件名前缀；code：0 成功、1 失败、2 频繁、3 重复、4 无权限、5 仅管理员",
    "doc": "interactions_interaction_id.put.html"
  },
].map(({doc, ...preset}) => ({...preset, documentation: `${officialApiDocsUrl}api/${doc}`}))

export const officialApiPresets = [...autogenApiPresets, ...extendedApiPresets]
export const presetCategories = [...new Set(officialApiPresets.map(item => item.category))]
