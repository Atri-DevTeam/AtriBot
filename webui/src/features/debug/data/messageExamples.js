const sampleKeyboard = {
  content: {
    rows: [
      {
        buttons: [
          {
            id: 'button-1',
            render_data: {
              label: '按钮',
              visited_label: '已点击',
              style: 1
            },
            action: {
              type: 2,
              data: '/help',
              enter: true,
              permission: {
                type: 2
              },
              unsupport_tips: '当前客户端版本不支持此按钮'
            }
          }
        ]
      }
    ]
  }
}
export const messageExamples = [
  {
    key: 'share-link',
    label: '机器人分享链接',
    method: 'POST',
    path: '/v2/generate_url_link',
    body: {
      callback_data: 'atrimeow'
    }
  },
  {
    key: 'bot-profile',
    label: '机器人详情',
    method: 'GET',
    path: '/users/@me',
    body: null
  },
  {
    key: 'group-info',
    label: '群聊 /info - 获取群基础信息',
    method: 'GET',
    path: '/v2/groups/{group_openid}/info',
    body: null
  },
  {
    key: 'group-bot-state',
    label: '群聊 /bot_state - 获取机器人群内状态',
    method: 'GET',
    path: '/v2/groups/{group_openid}/bot_state',
    body: null
  },
  {
    key: 'group-active-text',
    label: '群聊 /messages - 主动文本',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 0,
      content: 'hello'
    }
  },
  {
    key: 'private-active-text',
    label: '私聊 /messages - 主动文本',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 0,
      content: 'hello'
    }
  },
  {
    key: 'group-active-ref-text',
    label: '群聊 /messages - 引用文本',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 0,
      content: 'hello',
      message_reference: {
        message_id: 'REFIDX_xxx'
      }
    }
  },
  {
    key: 'group-active-markdown',
    label: '群聊 /messages - 主动 Markdown',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      markdown: {
        content: '# hello'
      }
    }
  },
  {
    key: 'private-active-markdown',
    label: '私聊 /messages - 主动 Markdown',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 2,
      markdown: {
        content: '# hello'
      }
    }
  },
  {
    key: 'group-active-markdown-keyboard',
    label: '群聊 /messages - Markdown + 按钮',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      markdown: {
        content: '# hello'
      },
      keyboard: sampleKeyboard
    }
  },
  {
    key: 'private-active-markdown-keyboard',
    label: '私聊 /messages - Markdown + 按钮',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 2,
      markdown: {
        content: '# hello'
      },
      keyboard: sampleKeyboard
    }
  },
  {
    key: 'group-reply-text',
    label: '群聊 /messages - 被动回复文本',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 0,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      content: 'reply'
    }
  },
  {
    key: 'private-reply-text',
    label: '私聊 /messages - 被动回复文本',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 0,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      content: 'reply'
    }
  },
  {
    key: 'group-reply-markdown',
    label: '群聊 /messages - 被动回复 Markdown',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      markdown: {
        content: '<qqbot-at-user id="{user_openid}" />\n# reply'
      }
    }
  },
  {
    key: 'private-reply-markdown',
    label: '私聊 /messages - 被动回复 Markdown',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 2,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      markdown: {
        content: '# reply'
      }
    }
  },
  {
    key: 'group-reply-markdown-keyboard',
    label: '群聊 /messages - 回复 Markdown + 按钮',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      markdown: {
        content: '<qqbot-at-user id="{user_openid}" />\n# reply'
      },
      keyboard: sampleKeyboard
    }
  },
  {
    key: 'private-reply-markdown-keyboard',
    label: '私聊 /messages - 回复 Markdown + 按钮',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 2,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      markdown: {
        content: '# reply'
      },
      keyboard: sampleKeyboard
    }
  },
  {
    key: 'group-event-markdown',
    label: '群聊 /messages - 事件 Markdown',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      event_id: 'EVENT_ID',
      markdown: {
        content: '# event reply'
      }
    }
  },
  {
    key: 'group-event-markdown-keyboard',
    label: '群聊 /messages - 事件 Markdown + 按钮',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 2,
      event_id: 'EVENT_ID',
      markdown: {
        content: '# event reply'
      },
      keyboard: sampleKeyboard
    }
  },
  {
    key: 'private-upload-image-url',
    label: '私聊 /files - 上传图片 URL',
    method: 'POST',
    path: '/v2/users/{user_openid}/files',
    body: {
      file_type: 1,
      url: 'https://example.com/a.png',
      srv_send_msg: false
    }
  },
  {
    key: 'private-upload-image-base64',
    label: '私聊 /files - 上传图片 Base64',
    method: 'POST',
    path: '/v2/users/{user_openid}/files',
    body: {
      file_type: 1,
      file_name: 'image.png',
      file_data: 'BASE64_DATA',
      srv_send_msg: false
    }
  },
  {
    key: 'group-upload-image-url',
    label: '群聊 /files - 上传图片 URL',
    method: 'POST',
    path: '/v2/groups/{group_openid}/files',
    body: {
      file_type: 1,
      url: 'https://example.com/a.png',
      srv_send_msg: false
    }
  },
  {
    key: 'group-upload-image-base64',
    label: '群聊 /files - 上传图片 Base64',
    method: 'POST',
    path: '/v2/groups/{group_openid}/files',
    body: {
      file_type: 1,
      file_name: 'image.png',
      file_data: 'BASE64_DATA',
      srv_send_msg: false
    }
  },
  {
    key: 'group-upload-file-url',
    label: '群聊 /files - 上传文件 URL',
    method: 'POST',
    path: '/v2/groups/{group_openid}/files',
    body: {
      file_type: 4,
      url: 'https://example.com/file.zip',
      srv_send_msg: false
    }
  },
  {
    key: 'private-send-media',
    label: '私聊 /messages - 发送媒体 file_info',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 7,
      media: {
        file_info: 'FILE_INFO'
      }
    }
  },
  {
    key: 'group-send-media',
    label: '群聊 /messages - 发送媒体 file_info',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 7,
      media: {
        file_info: 'FILE_INFO'
      }
    }
  },
  {
    key: 'private-reply-media',
    label: '私聊 /messages - 回复媒体 file_info',
    method: 'POST',
    path: '/v2/users/{user_openid}/messages',
    body: {
      msg_type: 7,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      media: {
        file_info: 'FILE_INFO'
      }
    }
  },
  {
    key: 'group-reply-media',
    label: '群聊 /messages - 回复媒体 file_info',
    method: 'POST',
    path: '/v2/groups/{group_openid}/messages',
    body: {
      msg_type: 7,
      msg_id: 'MESSAGE_ID',
      msg_seq: 1,
      media: {
        file_info: 'FILE_INFO'
      }
    }
  },
  {
    key: 'private-recall-message',
    label: '私聊 /messages - 撤回消息',
    method: 'DELETE',
    path: '/v2/users/{user_openid}/messages/{message_id}',
    body: null
  },
  {
    key: 'group-recall-message',
    label: '群聊 /messages - 撤回消息',
    method: 'DELETE',
    path: '/v2/groups/{group_openid}/messages/{message_id}',
    body: null
  },
  {
    key: 'group-member-info',
    label: '群聊 /members - 获取成员信息',
    method: 'GET',
    path: '/v2/groups/{group_openid}/members/{member_openid}',
    body: null
  }
]

