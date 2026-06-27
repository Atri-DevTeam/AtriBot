<template>
  <div class="shell">
    <div v-if="sidebarOpen" class="sidebar-backdrop show" @click="sidebarOpen = false"/>

    <aside class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="sidebar-head">
        <button class="sidebar-close" aria-label="关闭侧边栏" @click="sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="brand">
        <img v-if="botOpenId && appId" class="brand-avatar" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${botOpenId}/100`" referrerpolicy="no-referrer" />
        <div v-else class="brand-mark">A</div>
        <div>
          <h1>{{ botName }}</h1>
          <p>官方机器人WebUI</p>
        </div>
      </div>

      <nav class="side-nav">
        <button class="side-nav-item" :class="{ active: $route.path === '/' }" @click="$router.push('/'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          群聊
        </button>
        <button class="side-nav-item" :class="{ active: $route.path === '/c2c' }" @click="$router.push('/c2c'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
          </svg>
          私聊
        </button>
        <button class="side-nav-item" :class="{ active: $route.path === '/feedback' }" @click="$router.push('/feedback'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
          </svg>
          反馈管理
        </button>
      </nav>

      <div class="side-toolbar">
        <button class="ghost-button" :disabled="loadingGroups" @click="loadGroups">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </div>
    </aside>

    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2>群聊消息</h2>
        </div>
        <span class="status-pill topbar-status"><span class="dot ok"></span>{{ totalMessages }} 条记录</span>
      </header>

      <section class="content">
        <section class="chat-panel">
          <div class="chat-head">
            <div class="group-picker">
              <button class="group-picker-trigger" @click="dropdownOpen = !dropdownOpen; groupSearch = ''">
                <span>{{ selectedGroupId || '选择群聊' }}</span>
                <span class="arrow" :class="{ up: dropdownOpen }">▾</span>
              </button>
              <div v-if="dropdownOpen" class="dropdown-menu">
                <input v-model="groupSearch" class="dropdown-search" placeholder="搜索群聊 openId…" @click.stop />
                <button v-for="group in filteredGroups" :key="group.groupOpenId"
                        class="dropdown-item" :class="{ active: group.groupOpenId === selectedGroupId }"
                        @click="selectGroup(group.groupOpenId); dropdownOpen = false">
                  <span class="item-id">{{ group.groupOpenId }}</span>
                  <span class="item-badges">
                    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" :title="group.allowedActive ? '主动推送已开启' : '主动推送已关闭'">
                      <circle cx="8" cy="8" r="7" :fill="group.allowedActive ? '#10b981' : 'none'" :stroke="group.allowedActive ? '#10b981' : '#9ca3af'" stroke-width="1.5"/>
                      <path v-if="group.allowedActive" d="M4 7l3 3 5-5" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                      <template v-else><line x1="5" y1="5" x2="11" y2="11" stroke="#9ca3af" stroke-width="1.8" stroke-linecap="round"/><line x1="11" y1="5" x2="5" y2="11" stroke="#9ca3af" stroke-width="1.8" stroke-linecap="round"/></template>
                    </svg>
                    <span class="item-dot" :class="group.whitelist ? 'green' : 'gray'" :title="group.whitelist ? '白名单' : '未白名单'"></span>
                    <span class="item-dot" :class="group.blacklisted ? 'red' : 'gray'" :title="group.blacklisted ? '黑名单' : '未拉黑'"></span>
                  </span>
                </button>
              </div>
            </div>
            <div class="chat-head-right">
              <button class="info-toggle" :class="{ active: showInspector }" @click="showInspector = !showInspector" title="群信息" aria-label="群信息">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                  <line x1="12" y1="7" x2="12" y2="13"/>
                  <circle cx="12" cy="17" r="0.8" fill="currentColor" stroke="none"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="message-list" ref="messageListRef" @scroll="onScroll">
            <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
            <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>

            <div v-if="!selectedGroupId" class="empty-state">请选择一个群</div>
            <div v-else-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <article
              v-for="message in orderedMessages"
              :key="message.id"
              class="message"
              :class="{ mine: isMe(message) }"
            >
              <div class="avatar" @click="toggleMsgDetail(message.id)">
                <img
                  v-show="avatarUrl(message) && !avatarFailed[message.id]"
                  :src="avatarUrl(message)"
                  :alt="message.username"
                  @error="avatarFailed[message.id] = true"
                />
                <span v-show="!avatarUrl(message) || avatarFailed[message.id]">{{ avatarText(message) }}</span>
              </div>
              <div class="message-main">
                <div class="msg-header">
                  <template v-if="isMe(message)">
                    <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                      <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                      <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                    </svg>
                    <strong class="msg-name">{{ message.username || 'AtriBot' }}</strong>
                  </template>
                  <template v-else>
                    <strong class="msg-name">{{ message.username || 'Unknown' }}</strong>
                    <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                      <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                      <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                    </svg>
                  </template>
                  <span class="msg-uid" :class="{ expanded: expandedIds[message.id] }" v-if="message.unionOpenId">{{ message.unionOpenId }}</span>
                  <span v-if="!isMe(message) && message.memberRole" class="role-badge" :class="'role-' + message.memberRole.toLowerCase()">{{ roleLabel(message.memberRole) }}</span>
                </div>
                <div class="bubble" :class="{ recalled: recalledIds[message.messageOpenId] }"
                     @contextmenu.prevent.stop="onContextMenu($event, message)">
                  <pre v-if="recalledIds[message.messageOpenId]">你撤回了一条消息</pre>
                  <template v-else>
                    <div v-if="hasMsgRef(message)" class="msg-ref">
                    <span class="msg-ref-author">{{ parseMsgRef(message.messageReference).author }}</span>
                    <div class="msg-ref-content" v-html="renderRefContent(parseMsgRef(message.messageReference))"></div>
                  </div>
                    <div v-if="message.attachments" class="msg-attach">
                      <template v-for="(att, i) in parseAttach(message.attachments)" :key="message.id + '-' + i">
                        <img
                          v-show="!attachFailed[att.url]"
                          :src="att.url"
                          :alt="att.filename"
                          referrerpolicy="no-referrer"
                          class="clickable"
                          @error="attachFailed[att.url] = true"
                          @click="previewImg = att.url"
                        />
                        <span v-if="attachFailed[att.url]" class="attach-fail">📎 {{ att.filename }}</span>
                      </template>
                    </div>
                    <pre v-if="message.messageType !== 2 && message.messageType !== 7">{{ renderContent(message) }}</pre>
                    <div v-if="message.messageType === 7" class="media-placeholder">📷 媒体消息</div>
                    <div v-if="message.messageType === 2" class="md-body" v-html="renderMd(renderContent(message))"></div>
                  </template>
                </div>
                <div class="msg-time">{{ fmtTime(message.eventTimestamp || message.createdAt) }}</div>
              </div>
            </article>

          </div>

            <div v-if="replyTo" class="reply-bar">
              <span>{{ refMode ? '引用' : '回复' }} {{ replyTo.username || '...' }}</span>
              <button @click="replyTo = null; refMode = false">×</button>
            </div>
          <form class="composer" @submit.prevent="sendMessage">
            <div class="composer-type">
              <label :class="{ active: msgType === 'text' }"><input type="radio" v-model="msgType" value="text" />文本</label>
              <label :class="{ active: msgType === 'markdown' }"><input type="radio" v-model="msgType" value="markdown" />Markdown</label>
              <label :class="{ active: msgType === 'image' }"><input type="radio" v-model="msgType" value="image" />图片</label>
            </div>
            <textarea
              v-model="draft"
              :disabled="!selectedGroupId || sending"
              :placeholder="msgType === 'image' ? '图片 URL / Base64 / 直接粘贴图片' : msgType === 'markdown' ? 'Markdown 内容' : '文本消息'"
              rows="3"
              @paste="onPaste"
            ></textarea>
            <div class="composer-image-opts" v-if="msgType === 'image'">
              <label :class="{ active: imageType === 'url' }"><input type="radio" v-model="imageType" value="url" />URL</label>
              <label :class="{ active: imageType === 'base64' }"><input type="radio" v-model="imageType" value="base64" />Base64</label>
              <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
              <label @click="$refs.fileInputRef.click()">上传</label>
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="paste-preview" @click="pastePreview = null" title="点击清除" />
            <button class="primary-button" :disabled="!canSend">{{ sending ? '发送中' : '发送' }}</button>
          </form>

          <!-- 右键菜单 -->
          <div
            v-if="ctxMenu.visible"
            class="ctx-menu"
            :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
          >
            <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
                    @click="atUser(ctxMenu.message); ctxMenu.visible = false">@ 用户</button>
            <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
                    @click="openPermModal(ctxMenu.message)">更改权限组</button>
            <button @click="startReply(ctxMenu.message); ctxMenu.visible = false">回复</button>
            <button @click="startRefReply(ctxMenu.message); ctxMenu.visible = false">引用回复</button>
            <button @click="copyText(ctxMenu.message.content); ctxMenu.visible = false">复制</button>
            <button v-if="!recalledIds[ctxMenu.message.messageOpenId]"
                    class="ctx-recall"
                    @click="recallMsg(ctxMenu.message); ctxMenu.visible = false">撤回</button>
          </div>
        </section>

        <!-- 权限弹窗 -->
        <div v-if="showPermModal" class="perm-modal-backdrop" @click="showPermModal = false">
          <div class="perm-modal" @click.stop>
            <h3>更改权限组</h3>
            <p class="perm-uid">{{ permTarget }}</p>
            <div class="perm-roles">
              <button v-for="r in roles" :key="r" :class="['badge', 'clickable', pendingPermRole === r ? 'green' : 'gray']"
                      @click="pendingPermRole = r">{{ r }}</button>
            </div>
            <h4 style="margin:10px 0 4px">权限节点</h4>
            <div v-for="p in pendingPermNodes" :key="p" class="func-row">
              <span class="func-name">{{ p }}</span>
              <button class="perm-del" @click="removePermNode(p)">×</button>
            </div>
            <form class="perm-add" @submit.prevent="addPermNode(newPermNode)">
              <input v-model="newPermNode" placeholder="新权限节点" />
              <button class="primary-button" :disabled="!newPermNode.trim()">添加</button>
            </form>
            <div class="perm-modal-actions">
              <button class="ghost-button" @click="showPermModal = false">关闭</button>
              <button class="primary-button" @click="confirmPermRole(); showPermModal = false">确认</button>
            </div>
          </div>
        </div>

        <aside class="inspector" :class="{ 'inspector--show': showInspector }">
          <div class="inspector-head">
            <h3>群信息</h3>
            <button class="inspector-close" aria-label="关闭群信息" @click="showInspector = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <dl v-if="selectedGroup">
            <dt>群聊开放平台ID</dt>
            <dd>{{ selectedGroup.groupOpenId }}</dd>
            <dt>邀请人开放平台ID</dt>
            <dd>{{ selectedGroup.opMemberOpenId || '-' }}</dd>
            <dt>群聊状态</dt>
            <dd class="status-row">
              <span :class="['badge', 'clickable', selectedGroup.whitelist ? 'green' : 'gray']" @click="toggleStatus('whitelist')">白名单</span>
              <span :class="['badge', 'clickable', selectedGroup.blacklisted ? 'red' : 'gray']" @click="toggleStatus('blacklist')">黑名单</span>
              <span :class="['badge', 'clickable', selectedGroup.allowedActive ? 'green' : 'gray']" @click="toggleStatus('allowedActive')">主动推送</span>
            </dd>
            <dt>真实群号</dt>
            <dd>{{ selectedGroup.realGroupId || '-' }}</dd>
            <dt>群聊加入时间</dt>
            <dd>{{ formatTime(selectedGroup.timestamp) }}</dd>
          </dl>
          <div v-else class="hint">选择群后显示详情</div>

          <!-- 功能配置 -->
          <div v-if="selectedGroupId && funcEntries.length" class="func-box">
            <h4>功能列表</h4>
            <div v-for="[key, val] in funcEntries" :key="key" class="func-row clickable" @click="toggleFunction(key, !val.enabled)">
              <span class="func-name">{{ key }}</span>
              <span :class="['badge', val.enabled ? 'green' : 'gray']">{{ val.enabled ? '开' : '关' }}</span>
            </div>
          </div>

          <div class="log-box">
            <strong>请求状态</strong>
            <p>{{ notice }}</p>
          </div>
        </aside>
      </section>
    </main>

    <!-- 图片灯箱 -->
    <div v-if="previewImg" class="lightbox" @click="previewImg = null">
      <img :src="previewImg" referrerpolicy="no-referrer" @click.stop />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { LEGACY_TOKEN_KEY, API_BASE } from '../router.js'

const router = useRouter()

const groups = ref([])
const messages = ref([])
const selectedGroupId = ref('')
const loadingGroups = ref(false)
const loadingMessages = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const draft = ref('')
const msgType = ref('text')
const imageType = ref('url')

watch(msgType, () => { pastePreview.value = null })
const totalMessages = ref(0)
const notice = ref('等待操作')
const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')
const avatarFailed = reactive({})
const currentPage = ref(0)
const dropdownOpen = ref(false)
const groupSearch = ref('')
const sidebarOpen = ref(false)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const replyTo = ref(null)
const refMode = ref(false)
const funcEntries = ref([])
const showInspector = ref(false)
const showPermModal = ref(false)
const permTarget = ref('')
const pendingPermRole = ref('')
const pendingPermNodes = ref([])
const newPermNode = ref('')
const roles = ['USER', 'ADMIN', 'OWNER', 'BLACKLIST']
function roleLabel(r) {
  const map = { OWNER: '群主', ADMIN: '管理员', USER: '成员', MEMBER: '成员', BLACKLIST: '黑名单' }
  return map[r] || r
}
const attachFailed = reactive({})
const expandedIds = reactive({})
function toggleMsgDetail(id) { expandedIds[id] = !expandedIds[id] }
const previewImg = ref(null)
const pastePreview = ref(null)
const pageSize = 80
let eventSource = null

const messageListRef = ref(null)

const selectedGroup = computed(() => groups.value.find(g => g.groupOpenId === selectedGroupId.value))
const filteredGroups = computed(() => {
  const q = groupSearch.value.toLowerCase()
  const filtered = q ? groups.value.filter(g => g.groupOpenId.toLowerCase().includes(q)) : groups.value
  return [...filtered].sort((a, b) => {
    if (a.whitelist !== b.whitelist) return a.whitelist ? -1 : 1
    return b.timestamp - a.timestamp
  })
})
const hasMore = computed(() => messages.value.length < totalMessages.value)
const orderedMessages = computed(() => [...messages.value].reverse())
const canSend = computed(() => {
  return !(!selectedGroupId.value || !draft.value.trim() || sending.value);

})

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  await loadConfig()
  await loadGroups()
  connectSse()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})

function connectSse() {
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`, { withCredentials: true })
  eventSource.onmessage = async (e) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.type !== 'refresh') return
      if (payload.groupOpenId !== selectedGroupId.value) return
      const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/messages?page=1&pageSize=${pageSize}`)
      const latest = data.records || []
      const seen = new Set(messages.value.map(m => m.messageOpenId))
      const fresh = latest.filter(m => !seen.has(m.messageOpenId))
      if (fresh.length > 0) {
        messages.value = [...fresh, ...messages.value]
        totalMessages.value = data.total || totalMessages.value
        if (isNearBottom()) {
          nextTick(() => scrollToBottom())
        }
      }
    } catch { /* ignore */ }
  }
  eventSource.onerror = () => {
    eventSource.close()
    setTimeout(connectSse, 5000)
  }
}

function onPaste(e) {
  if (msgType.value !== 'image') return
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const blob = item.getAsFile()
      const reader = new FileReader()
      reader.onload = () => {
        const b64 = reader.result.split(',')[1]
        draft.value = b64
        imageType.value = 'base64'
        pastePreview.value = reader.result
      }
      reader.readAsDataURL(blob)
      return
    }
  }
}

function onFilePicked(e) {
  const file = e.target.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  const reader = new FileReader()
  reader.onload = () => {
    const b64 = reader.result.split(',')[1]
    draft.value = b64
    imageType.value = 'base64'
    pastePreview.value = reader.result
  }
  reader.readAsDataURL(file)
  e.target.value = ''
}

async function toggleStatus(type) {
  if (!selectedGroupId.value) return
  const keyMap = { whitelist: 'whitelist', blacklist: 'blacklisted', allowedActive: 'allowedActive' }
  const key = keyMap[type]
  const old = selectedGroup.value[key]
  try {
    await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/${type}?enabled=${!old}`, { method: 'POST' })
    // 更新本地 selectedGroup
    const g = selectedGroup.value
    if (type === 'whitelist') g.whitelist = !old
    else if (type === 'blacklist') g.blacklisted = !old
    else g.allowedActive = !old
  } catch (e) { notice.value = e.message }
}

async function toggleFunction(funcKey, enabled) {
  if (!selectedGroupId.value) return
  try {
    await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/functions/${encodeURIComponent(funcKey)}?enabled=${enabled}`, { method: 'POST' })
    // 更新本地
    const entry = funcEntries.value.find(([k]) => k === funcKey)
    if (entry) entry[1].enabled = enabled
  } catch (e) { notice.value = e.message }
}

async function loadGroupFunctions() {
  if (!selectedGroupId.value) return
  try {
    const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/functions`)
    funcEntries.value = data ? Object.entries(data) : []
  } catch { funcEntries.value = [] }
}

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function onDocumentClick(e) {
  if (!e.target.closest('.group-picker')) dropdownOpen.value = false
  if (!e.target.closest('.ctx-menu')) ctxMenu.visible = false
}

function onContextMenu(e, message) {
  ctxMenu.visible = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.message = message
}

async function openPermModal(message) {
  permTarget.value = message.unionOpenId
  try {
    const data = await api(`/c2c/${encodeURIComponent(message.unionOpenId)}/permissions`)
    pendingPermRole.value = data?.role || 'USER'
    pendingPermNodes.value = [...(data?.permissions || [])]
  } catch { pendingPermRole.value = 'USER'; pendingPermNodes.value = [] }
  newPermNode.value = ''
  showPermModal.value = true
}

async function confirmPermRole() {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/role?role=${pendingPermRole.value}`, { method: 'POST' })
  } catch (e) { notice.value = e.message }
}

async function addPermNode(perm) {
  if (!permTarget.value || !perm?.trim()) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm.trim())}?enabled=true`, { method: 'POST' })
    pendingPermNodes.value.push(perm.trim())
    newPermNode.value = ''
  } catch (e) { notice.value = e.message }
}

async function removePermNode(perm) {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm)}?enabled=false`, { method: 'POST' })
    pendingPermNodes.value = pendingPermNodes.value.filter(p => p !== perm)
  } catch (e) { notice.value = e.message }
}

function atUser(message) {
  const tag = `@${message.unionOpenId}`
  if (draft.value) {
    draft.value = draft.value + ' ' + tag
  } else {
    draft.value = tag
  }
}

async function copyText(text) {
  try { await navigator.clipboard.writeText(text || '') } catch { /* ignore */ }
}

function startReply(message) {
  replyTo.value = message
  refMode.value = false
}

function startRefReply(message) {
  replyTo.value = message
  refMode.value = true
}

async function recallMsg(message) {
  try {
    await api('/groups/recall', {
      method: 'POST',
      body: JSON.stringify({groupOpenId: message.groupOpenId, messageId: message.messageOpenId})
    })
    recalledIds[message.messageOpenId] = true
    notice.value = '消息已撤回'
  } catch (e) {
    notice.value = e.message
  }
}

function authHeaders() {
  return {
    'Content-Type': 'application/json'
  }
}

async function api(path, options) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (response.status === 503) {
    logout('WebUI 已关闭')
    throw new Error('WebUI 已关闭')
  }
  const payload = await response.json()
  if (response.status === 401) {
    logout(payload.message || '登录已失效')
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
  } catch { /* non-critical */ }
}

function isMe(message) {
  return botOpenId.value && message.unionOpenId === botOpenId.value
}

function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts.includes('T') ? ts : ts.replace(' ', 'T'))
  if (isNaN(d.getTime())) return ts
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function hasMsgRef(message) {
  try {
    const raw = message.messageReference
    if (!raw) return false
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) && arr.length > 0
  } catch { return false }
}

function renderContent(message) {
  let text = message.content || ''
  text = text.replace(/<faceType=\d+,faceId="[^"]*",ext="[^"]*">/g, '[表情]')
  text = text.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  text = text.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  text = text.replace(/(<@[A-F0-9]+>)\s+\1/g, '$1')
  if (message.eventType === 'GROUP_MESSAGE_CREATE' && message.mentions) {
    try {
      const mentions = typeof message.mentions === 'string' ? JSON.parse(message.mentions) : message.mentions
      if (Array.isArray(mentions)) {
        for (const m of mentions) {
          if (m.userId && m.username) {
            text = text.replaceAll(`<@${m.userId}>`, `@${m.username}`)
          }
        }
      }
    } catch { /* ignore */ }
  }
  return text
}

function renderRefContent(ref) {
  const parts = []
  if (ref.content) {
    let t = ref.content
    t = t.replace(/<faceType=\d+,faceId="[^"]*",ext="[^"]*">/g, '[表情]')
    t = t.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
    t = t.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
    if (t.trim()) parts.push(renderMd(t))
  }
  if (ref.attachments && ref.attachments.length) {
    for (const a of ref.attachments) {
      parts.push(`<img src="${a.url}" referrerpolicy="no-referrer" style="max-width:120px;max-height:80px;border-radius:4px;display:block" onerror="this.outerHTML='<span style=font-size:12px;color:#94a3b8>📷 图片</span>'">`)
    }
  }
  if (!parts.length) return '&#8203;'
  return parts.join('')
}

function parseMsgRef(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr) || !arr[0]) return {}
    const ref = arr[0]
    return {
      author: ref.author?.username || '',
      content: ref.content || '',
      attachments: (ref.attachments || []).filter(a => a.url)
    }
  } catch { return {} }
}

function parseAttach(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr.filter(a => a.url && (a.content_type || '').startsWith('image/'))
  } catch { return [] }
}

function renderMd(text) {
  if (!text) return ''
  let html = text

  // 代码块 ``` ... ``` (在 HTML 转义之前处理)
  const codeBlocks = []
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
    codeBlocks.push({ lang, code: code.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/&/g, '&amp;') })
    return `CODE${codeBlocks.length - 1}`
  })

  // 转义 HTML
  html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  // 图片 ![alt #Wpx #Hpx](url)
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_, alt, url) => {
    const m = alt.match(/#(\d+)px\s*#(\d+)px/)
    let style = ''
    let cleanAlt = alt
    if (m) {
      style = `max-width:100%;max-height:320px;width:${m[1]}px;height:auto`
      cleanAlt = alt.replace(m[0], '').trim()
    }
    return `<img src="${url}" alt="${cleanAlt}" style="${style}">`
  })

  // 链接 [text](url) 和 <url>
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
  html = html.replace(/<(https?:\/\/[^>]+)>/g, '<a href="$1" target="_blank">$1</a>')

  // 水平分割线
  html = html.replace(/^(\*{3,}|-{3,})$/gm, '<hr>')

  // 块引用
  html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>')
  html = html.replace(/<\/blockquote>\n?<blockquote>/g, '\n')

  // 标题
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')

  // 有序列表 (行首 "1. " 或 "2. " 等)
  html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
  // 无序列表 (行首 "- " 或 "* ")
  html = html.replace(/^[\-*] (.+)$/gm, '<li>$1</li>')
  // 包裹连续的 <li>
  html = html.replace(/(<li>.+<\/li>(\n|$))+/g, '<ul>$&</ul>')

  // 行内样式
  html = html.replace(/~~(.+?)~~/g, '<del>$1</del>')
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__([^_]+)__/g, '<strong><u>$1</u></strong>')
  html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')
  html = html.replace(/(?<!_)_(?!_)(.+?)(?<!_)_(?!_)/g, '<em>$1</em>')
  html = html.replace(/`(.+?)`/g, '<code>$1</code>')

  // 换行：连续 \n 合并为一个段落间距
  html = html.replace(/\n{2,}/g, '\n')
  html = html.replace(/\n/g, '<br>')

  // 还原代码块
  html = html.replace(/CODE(\d+)/g, (_, i) => {
    const b = codeBlocks[+i]
    return `<pre><code>${b.code}</code></pre>`
  })

  return html
}

function avatarUrl(message) {
  if (!appId.value) return null
  if (isMe(message)) return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${botOpenId.value}/640`
  if (!message.unionOpenId) return null
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${message.unionOpenId}/640`
}

async function logout(message = '已退出登录') {
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'same-origin'
    })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  router.replace('/login')
}

async function loadGroups() {
  loadingGroups.value = true
  try {
    groups.value = await api('/groups')
    notice.value = `已加载 ${groups.value.length} 个群`
  } catch (error) {
    notice.value = error.message
  } finally {
    loadingGroups.value = false
  }
}

async function selectGroup(groupOpenId) {
  if (selectedGroupId.value === groupOpenId) return
  selectedGroupId.value = groupOpenId
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  funcEntries.value = []
  await loadLatestMessages()
  loadGroupFunctions()
}

async function loadLatestMessages() {
  if (!selectedGroupId.value) return
  loadingMessages.value = true
  currentPage.value = 1
  try {
    const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/messages?page=1&pageSize=${pageSize}`)
    messages.value = data.records || []
    totalMessages.value = data.total || 0
    notice.value = `已加载 ${messages.value.length} 条消息`
    await nextTick()
    scrollToBottom()
  } catch (error) {
    notice.value = error.message
  } finally {
    loadingMessages.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loadingMessages.value) return
  loadingMore.value = true
  const el = messageListRef.value
  const prevHeight = el ? el.scrollHeight : 0
  currentPage.value++
  try {
    const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/messages?page=${currentPage.value}&pageSize=${pageSize}`)
    const older = data.records || []
    messages.value = [...messages.value, ...older]
    notice.value = `已加载 ${messages.value.length} / ${totalMessages.value} 条消息`
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  } catch (error) {
    notice.value = error.message
    currentPage.value--
  } finally {
    loadingMore.value = false
  }
}

function onScroll() {
  const el = messageListRef.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop < 60) {
    loadMore()
  }
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function sendMessage() {
  if (!canSend.value) return
  sending.value = true
  try {
    const body = {
      groupOpenId: selectedGroupId.value,
      msgType: msgType.value,
      content: draft.value.trim()
    }
    if (msgType.value === 'markdown') {
      body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
    }
    if (msgType.value === 'image') {
      body.imageType = imageType.value
      body.imageValue = draft.value.trim()
    }
    if (replyTo.value) {
      if (refMode.value) {
        body.refMessageId = replyTo.value.refIdx
        body.refAuthor = replyTo.value.username || ''
        body.refContent = replyTo.value.content || ''
        body.refAttachments = replyTo.value.attachments || null
      } else {
        body.replyMessageId = replyTo.value.messageOpenId
      }
    }
    await api('/groups/send', {
      method: 'POST',
      body: JSON.stringify(body)
    })
    draft.value = ''
    pastePreview.value = null
    replyTo.value = null
    refMode.value = false
    notice.value = '消息已发送'
    await loadLatestMessages()
  } catch (error) {
    notice.value = error.message
  } finally {
    sending.value = false
  }
}

function shortId(value) {
  if (!value) return '-'
  if (value.length <= 18) return value
  return `${value.slice(0, 8)}...${value.slice(-6)}`
}

function formatTime(ts) {
  if (!ts || ts <= 0) return '-'
  return new Date(ts * 1000).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
}

function avatarText(message) {
  const name = message.username || (isMe(message) ? 'Bot' : '?')
  return name.slice(0, 1).toUpperCase()
}
</script>
