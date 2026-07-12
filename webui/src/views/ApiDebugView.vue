<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" @click="resetRequest">重置</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>

    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2>调试</h2>
        </div>
      </header>

      <section class="content debug-layout">
        <section class="chat-panel debug-panel">
          <div class="chat-head">
            <strong>官方 API 请求</strong>
            <button class="primary-button debug-send-btn" :disabled="loading || !path.trim()" @click="sendRequest">
              {{ loading ? '请求中' : '发送' }}
            </button>
          </div>
          <div class="debug-body">
            <form class="debug-request" @submit.prevent="sendRequest">
              <label class="debug-preset-line">
                <span>预设</span>
                <select v-model="selectedPreset" class="debug-preset" @change="applyPreset">
                  <option value="">-- 选择接口样例 --</option>
                  <option v-for="item in debugPresets" :key="item.key" :value="item.key">
                    {{ item.label }}
                  </option>
                </select>
              </label>
              <div class="debug-url-line">
                <select v-model="method" class="debug-method">
                  <option v-for="item in methods" :key="item" :value="item">{{ item }}</option>
                </select>
                <input class="debug-path" v-model="path" spellcheck="false" placeholder="/v2/groups/{group_openid}/messages" />
              </div>
              <div class="debug-editors">
                <label>
                  <span class="debug-editor-title">
                    <span>Headers</span>
                    <button type="button" class="debug-format-btn" @click="openEditor('headers')">放大</button>
                  </span>
                  <textarea v-model="headers" spellcheck="false" placeholder="X-Union-Appid: ..."></textarea>
                </label>
                <label>
                  <span class="debug-editor-title">
                    <span>Body</span>
                    <span class="debug-editor-actions">
                      <button type="button" class="debug-format-btn" @click="formatBodyJson">格式化</button>
                      <button type="button" class="debug-format-btn" @click="openEditor('body')">放大</button>
                    </span>
                  </span>
                  <textarea v-model="body" spellcheck="false" placeholder="{&#10;  &quot;content&quot;: &quot;hello&quot;&#10;}"></textarea>
                </label>
              </div>
            </form>
          </div>
        </section>

        <section class="chat-panel debug-panel">
          <div class="chat-head">
            <strong>响应结果</strong>
            <div v-if="result" class="debug-response-status">
              <span :class="['badge', result.statusCode >= 200 && result.statusCode < 300 ? 'green' : 'red']">{{ result.statusCode }}</span>
              <span>{{ result.durationMillis }}ms</span>
            </div>
          </div>
          <div class="debug-body debug-response-body">
            <div v-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="loading" class="empty-state">正在请求官方 API</div>
            <div v-else-if="!result" class="empty-state">请求结果会显示在这里</div>
            <template v-else>
              <div class="debug-meta">
                <span>{{ result.method }}</span>
                <span class="debug-final-url">{{ result.url }}</span>
              </div>
              <details>
                <summary>响应头</summary>
                <pre>{{ formatJson(result.headers) }}</pre>
              </details>
              <pre class="debug-output">{{ responseText }}</pre>
            </template>
          </div>
        </section>
      </section>

      <div v-if="expandedEditor" class="debug-modal-backdrop" @click.self="closeEditor">
        <section class="debug-modal" role="dialog" aria-modal="true" :aria-label="expandedEditor === 'headers' ? 'Headers 编辑器' : 'Body 编辑器'">
          <div class="debug-modal-head">
            <strong>{{ expandedEditor === 'headers' ? 'Headers' : 'Body' }}</strong>
            <div class="debug-modal-actions">
              <button v-if="expandedEditor === 'body'" type="button" class="debug-format-btn" @click="formatBodyJson">格式化</button>
              <button type="button" class="debug-format-btn" @click="closeEditor">关闭</button>
            </div>
          </div>
          <textarea
            v-if="expandedEditor === 'headers'"
            v-model="headers"
            class="debug-modal-textarea"
            spellcheck="false"
            placeholder="X-Union-Appid: ..."
          ></textarea>
          <textarea
            v-else
            v-model="body"
            class="debug-modal-textarea"
            spellcheck="false"
            placeholder="{&#10;  &quot;content&quot;: &quot;hello&quot;&#10;}"
          ></textarea>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { API_BASE, LEGACY_TOKEN_KEY } from '../router.js'
import router from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']
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
const debugPresets = [
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/files',
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
    path: '/v2/users/{open_id}/files',
    body: {
      file_type: 1,
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
      file_type: 1,
      url: 'https://example.com/file.zip',
      srv_send_msg: false
    }
  },
  {
    key: 'private-send-media',
    label: '私聊 /messages - 发送媒体 file_info',
    method: 'POST',
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/users/{open_id}/messages',
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
    path: '/v2/groups/{group_openid}/members/{user_openid}',
    body: null
  }
]

const sidebarOpen = ref(false)
const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')
const selectedPreset = ref('')
const method = ref('GET')
const path = ref('/gateway')
const headers = ref('')
const body = ref('')
const expandedEditor = ref('')
const loading = ref(false)
const error = ref('')
const result = ref(null)

const responseText = computed(() => {
  if (!result.value) return ''
  const raw = result.value.body || ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw || '(空响应)'
  }
})

onMounted(loadConfig)

async function api(apiPath, options) {
  const response = await fetch(`${API_BASE}${apiPath}`, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    ...options
  })
  if (response.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
  let payload
  try {
    payload = await response.json()
  } catch {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  if (response.status === 401) {
    logout()
    throw new Error(payload.message || '未授权')
  }
  if (payload.status !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

async function loadConfig() {
  try {
    const data = await api('/config')
    appId.value = data.appId || ''
    botOpenId.value = data.botOpenId || ''
    botName.value = data.botName || 'AtriBot'
  } catch { /* ignore */ }
}

async function sendRequest() {
  if (!path.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    result.value = await api('/debug/official/request', {
      method: 'POST',
      body: JSON.stringify({
        method: method.value,
        path: path.value.trim(),
        headers: parseHeaders(headers.value),
        body: body.value
      })
    })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function parseHeaders(raw) {
  const parsed = {}
  for (const line of String(raw || '').split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const index = trimmed.indexOf(':')
    if (index <= 0) continue
    const name = trimmed.slice(0, index).trim()
    const value = trimmed.slice(index + 1).trim()
    if (name) parsed[name] = value
  }
  return parsed
}

function formatJson(value) {
  try {
    return JSON.stringify(value || {}, null, 2)
  } catch {
    return String(value || '')
  }
}

function formatBodyJson() {
  const raw = body.value.trim()
  if (!raw) return
  try {
    body.value = JSON.stringify(JSON.parse(raw), null, 2)
    error.value = ''
  } catch (e) {
    error.value = 'Body 不是合法 JSON，无法格式化'
  }
}

function openEditor(type) {
  expandedEditor.value = type
}

function closeEditor() {
  expandedEditor.value = ''
}

function applyPreset() {
  const preset = debugPresets.find(item => item.key === selectedPreset.value)
  if (!preset) return
  method.value = preset.method
  path.value = preset.path
  body.value = preset.body == null ? '' : JSON.stringify(preset.body, null, 2)
  error.value = ''
  result.value = null
}

function resetRequest() {
  selectedPreset.value = ''
  method.value = 'GET'
  path.value = '/gateway'
  headers.value = ''
  body.value = ''
  error.value = ''
  result.value = null
}

async function logout() {
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  router.replace('/login')
}
</script>
