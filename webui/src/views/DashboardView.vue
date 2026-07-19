<template>
  <!-- legacy-chat：聊天页沿用 layout.css / responsive.css 的原始视觉，polish.css 的
       新版外观层不作用于此，避免桌面端卡片样式泄漏到手机端布局上 -->
  <div class="shell legacy-chat">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loadingGroups" @click="loadGroups">刷新</button>
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
          <h2>群聊消息</h2>
        </div>
        <span class="status-pill topbar-status"><span class="dot ok"></span>{{ totalMessages }} 条记录</span>
      </header>

      <section class="content">
        <section class="chat-panel">
          <div class="chat-head">
            <div class="group-picker">
              <div class="group-picker-row">
                <button class="group-picker-trigger" @click="toggleDropdown">
                  <span>{{ selectedGroupId || '选择群聊' }}</span>
                  <span class="arrow" :class="{ up: dropdownOpen }">▾</span>
                </button>
                <button class="filter-return-btn" type="button" title="返回群聊总览" aria-label="返回群聊总览" @click="returnToGroupFilter">
                  <!-- 九宫格＝“回到总览”，比原来那三条横线（更像筛选/汉堡菜单）语义清楚 -->
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round">
                    <rect x="3.25" y="3.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="13.25" y="3.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="3.25" y="13.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="13.25" y="13.25" width="7.5" height="7.5" rx="1.8"/>
                  </svg>
                </button>
              </div>
              <div v-if="dropdownOpen" ref="dropdownRef" class="dropdown-menu">
                <input v-model="groupSearch" class="dropdown-search" placeholder="搜索群聊 openId…" @click.stop />
                <button v-for="group in filteredGroups" :key="group.groupOpenId"
                        class="dropdown-item" :class="{ active: group.groupOpenId === selectedGroupId }"
                        @click="selectGroup(group.groupOpenId); closeDropdown()">
                  <span class="item-id">{{ group.groupOpenId }}</span>
                  <span class="item-badges">
                    <svg :class="['group-status-mark', 'status-active', { 'is-enabled': group.allowedActive }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.allowedActive ? '主动消息已开启' : '主动消息已关闭' }}</title>
                      <path class="status-surface" d="M4.5 6.8A3.3 3.3 0 0 1 7.8 3.5h7.7a3.3 3.3 0 0 1 3.3 3.3v4.7a3.3 3.3 0 0 1-3.3 3.3h-5.1l-4.2 3.7v-4.1a3.3 3.3 0 0 1-1.7-2.9V6.8Z"/>
                      <path class="status-line" d="M14.9 7.1c1.1.7 1.8 1.8 1.8 3.1s-.7 2.4-1.8 3.1M12.7 8.7c.5.4.8.9.8 1.5s-.3 1.1-.8 1.5"/>
                    </svg>
                    <svg :class="['group-status-mark', 'status-whitelist', { 'is-enabled': group.whitelist }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.whitelist ? '白名单' : '未加入白名单' }}</title>
                      <path class="status-surface" d="M12 3.2 19 6v5.1c0 4.3-2.5 7.5-7 9.7-4.5-2.2-7-5.4-7-9.7V6l7-2.8Z"/>
                      <path class="status-line" d="m8.4 11.8 2.2 2.2 5-5"/>
                    </svg>
                    <svg :class="['group-status-mark', 'status-blacklist', { 'is-enabled': group.blacklisted }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.blacklisted ? '黑名单' : '未加入黑名单' }}</title>
                      <path class="status-surface" d="m8 3.5-4.5 4.5v8L8 20.5h8l4.5-4.5V8L16 3.5H8Z"/>
                      <path class="status-line" d="M8 12h8"/>
                    </svg>
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

            <div v-if="!selectedGroupId" class="group-list-hint">
              <div class="group-list-grid">
                <button v-for="group in visibleGroups" :key="group.groupOpenId"
                        class="group-list-card"
                        @click="selectGroup(group.groupOpenId)">
                  <span class="group-list-id">{{ group.groupOpenId }}</span>
                  <span class="group-list-badges">
                    <svg :class="['group-status-mark', 'group-status-mark--card', 'status-active', { 'is-enabled': group.allowedActive }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.allowedActive ? '主动消息已开启' : '主动消息已关闭' }}</title>
                      <path class="status-surface" d="M4.5 6.8A3.3 3.3 0 0 1 7.8 3.5h7.7a3.3 3.3 0 0 1 3.3 3.3v4.7a3.3 3.3 0 0 1-3.3 3.3h-5.1l-4.2 3.7v-4.1a3.3 3.3 0 0 1-1.7-2.9V6.8Z"/>
                      <path class="status-line" d="M14.9 7.1c1.1.7 1.8 1.8 1.8 3.1s-.7 2.4-1.8 3.1M12.7 8.7c.5.4.8.9.8 1.5s-.3 1.1-.8 1.5"/>
                    </svg>
                    <svg :class="['group-status-mark', 'group-status-mark--card', 'status-whitelist', { 'is-enabled': group.whitelist }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.whitelist ? '白名单' : '未加入白名单' }}</title>
                      <path class="status-surface" d="M12 3.2 19 6v5.1c0 4.3-2.5 7.5-7 9.7-4.5-2.2-7-5.4-7-9.7V6l7-2.8Z"/>
                      <path class="status-line" d="m8.4 11.8 2.2 2.2 5-5"/>
                    </svg>
                    <svg :class="['group-status-mark', 'group-status-mark--card', 'status-blacklist', { 'is-enabled': group.blacklisted }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ group.blacklisted ? '黑名单' : '未加入黑名单' }}</title>
                      <path class="status-surface" d="m8 3.5-4.5 4.5v8L8 20.5h8l4.5-4.5V8L16 3.5H8Z"/>
                      <path class="status-line" d="M8 12h8"/>
                    </svg>
                  </span>
                </button>
                <div v-if="groups.length === 0" class="empty-state">暂无群聊数据</div>
                <div v-else-if="visibleGroups.length === 0" class="empty-state">没有匹配的群</div>
              </div>
            </div>
            <div v-else-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <article
              v-for="message in orderedMessages"
              :key="message.id"
              :data-message-id="message.id"
              class="message"
              :class="{ mine: isMe(message), highlighted: highlightedMessageId === message.id }"
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
                <div class="msg-header" :class="{ 'uid-expanded': expandedIds[message.id] }">
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
                    <div v-if="hasMsgRef(message)"
                         class="msg-ref msg-ref--clickable"
                         title="跳转到引用来源"
                         @click.stop="jumpToReference(message)">
                    <span class="msg-ref-author">{{ parseMsgRef(message.messageReference).author }}</span>
                    <div class="msg-ref-content" v-html="renderRefContent(parseMsgRef(message.messageReference))"></div>
                  </div>
                    <div v-if="message.attachments" class="msg-attach">
                      <template v-for="(att, i) in parseAttach(message.attachments)" :key="message.id + '-' + i">
                        <img
                          v-if="att.type === 'image'"
                          v-show="!attachFailed[att.url]"
                          :src="att.url"
                          :alt="att.filename"
                          referrerpolicy="no-referrer"
                          class="clickable"
                          @error="attachFailed[att.url] = true"
                          @click="previewImg = att.url"
                        />
                        <span v-if="att.type === 'image' && attachFailed[att.url]" class="attach-fail">📎 {{ att.filename }}</span>
                        <div v-else-if="att.type === 'voice'" class="voice-attach">
                          <div class="voice-title">语音消息</div>
                          <div v-if="att.asrText" class="voice-asr">{{ att.asrText }}</div>
                          <audio v-if="att.voiceUrl" :src="att.voiceUrl" controls preload="none"></audio>
                          <a v-else-if="att.url" :href="att.url" target="_blank" rel="noreferrer">打开原始音频</a>
                        </div>
                      </template>
                    </div>
                    <pre v-if="message.messageType !== 2 && message.messageType !== 7 && renderContent(message)">{{ renderContent(message) }}</pre>
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
                    @click="openPermModal(ctxMenu.message)">更改信息</button>
            <button @click="startReply(ctxMenu.message); ctxMenu.visible = false">回复</button>
            <button @click="startRefReply(ctxMenu.message); ctxMenu.visible = false">引用回复</button>
            <button @click="copyText(ctxMenu.message.content); ctxMenu.visible = false">复制</button>
            <button v-if="!recalledIds[ctxMenu.message.messageOpenId]"
                    class="ctx-recall"
                    @click="recallMsg(ctxMenu.message); ctxMenu.visible = false">撤回</button>
          </div>
        </section>

        <!-- 更改信息弹窗 -->
        <div v-if="showPermModal" class="perm-modal-backdrop" @click="showPermModal = false">
          <div class="perm-modal" @click.stop>
            <h3>更改信息</h3>
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
            <h4 style="margin:10px 0 4px">状态</h4>
            <label class="checkbox-label">
              <input type="checkbox" v-model="pendingIsBlocked" />
              拉黑（禁止使用指令）
            </label>
            <label class="checkbox-label">
              <input type="checkbox" v-model="pendingIsIgnored" />
              屏蔽（静默忽略所有交互）
            </label>
            <label class="checkbox-label">
              <input type="checkbox" v-model="pendingC2CPush" />
              主动消息
            </label>
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
              <span :class="['badge', selectedGroup.allowedActive ? 'green' : 'gray']">主动推送</span>
            </dd>
            <dt>真实群号</dt>
            <dd>
              <input v-model="realGroupInput" class="real-group-input" placeholder="输入真实群号"
                     @blur="saveRealGroup" @keydown.enter="saveRealGroup" />
            </dd>
            <dt>群聊加入时间</dt>
            <dd>{{ formatTime(selectedGroup.timestamp) }}</dd>
          </dl>
          <div v-else class="group-filter-panel">
            <div class="filter-title">群筛选</div>
            <div class="filter-actions">
              <button v-for="option in groupFilterOptions"
                      :key="option.value"
                      :class="['filter-chip', { active: groupFilter === option.value }]"
                      @click="setGroupFilter(option.value)">
                {{ option.label }}
              </button>
            </div>
            <label v-if="groupFilter === 'function'" class="function-filter">
              <span>Function</span>
              <select v-model="functionFilterKey" :disabled="functionFilterLoading">
                <option value="">任意已开启功能</option>
                <option v-for="key in availableFunctionKeys" :key="key" :value="key">{{ key }}</option>
              </select>
            </label>
            <div class="filter-summary">
              <span v-if="functionFilterLoading">正在加载功能配置</span>
              <span v-else>{{ visibleGroups.length }} / {{ groups.length }} 个群</span>
            </div>
          </div>

          <!-- 功能配置 -->
          <div v-if="selectedGroupId" class="func-box">
            <h4>功能列表</h4>
            <div v-for="[key, val] in funcEntries" :key="key" class="func-row clickable" @click="toggleFunction(key, !val.enabled)">
              <span class="func-name">{{ key }}</span>
              <span :class="['badge', val.enabled ? 'green' : 'gray']">{{ val.enabled ? '开' : '关' }}</span>
            </div>
            <div v-if="funcEntries.length === 0" class="func-empty">暂无功能配置</div>
            <form class="perm-add func-add" @submit.prevent="addFunctionKey">
              <select v-model="newFunctionKey">
                <option value="">选择功能配置</option>
                <option v-for="key in addableFunctionKeys" :key="key" :value="key">{{ key }}</option>
              </select>
              <button class="primary-button" :disabled="!newFunctionKey">添加</button>
            </form>
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
import { useRouter, useRoute } from 'vue-router'
import { LEGACY_TOKEN_KEY, API_BASE } from '../router.js'
import { renderFaceTags } from '../messageRender.js'
import { escapeHtml, renderMarkdown as renderMd } from '../lib/markdown.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()
const route = useRoute()

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
const dropdownRef = ref(null)
const dropdownScrollTop = ref(0)
const groupSearch = ref('')
const groupFilter = ref('all')
const functionFilterKey = ref('')
const functionFilterLoading = ref(false)
const knownFunctionKeys = ref([])
const groupFunctionConfigs = reactive({})
const sidebarOpen = ref(false)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const replyTo = ref(null)
const refMode = ref(false)
const funcEntries = ref([])
const newFunctionKey = ref('')
const showInspector = ref(false)
const showPermModal = ref(false)
const permTarget = ref('')
const pendingPermRole = ref('')
const pendingPermNodes = ref([])
const pendingIsBlocked = ref(false)
const pendingIsIgnored = ref(false)
const pendingC2CPush = ref(true)
const newPermNode = ref('')
const roles = ['USER', 'ADMIN', 'OWNER']
function roleLabel(r) {
  const map = { OWNER: '群主', ADMIN: '管理员', USER: '成员', MEMBER: '成员' }
  return map[r] || r
}
const attachFailed = reactive({})
const expandedIds = reactive({})
function toggleMsgDetail(id) { expandedIds[id] = !expandedIds[id] }
const previewImg = ref(null)
const pastePreview = ref(null)
const highlightedMessageId = ref(null)
const pageSize = 80
let eventSource = null
let highlightTimer = null
const GROUP_FILTER_STORAGE_KEY = 'atri.webui.group_filter'

const messageListRef = ref(null)

const selectedGroup = computed(() => groups.value.find(g => g.groupOpenId === selectedGroupId.value))
const realGroupInput = ref('')
watch(selectedGroup, (g) => { realGroupInput.value = g?.realGroupId || '' })
async function saveRealGroup() {
  const g = selectedGroup.value
  if (!g) return
  const val = realGroupInput.value.trim()
  const original = g.realGroupId || ''
  if (val === original) return
  try {
    await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/real-group-id?value=${encodeURIComponent(val || 'null')}`, { method: 'POST' })
    g.realGroupId = val ? parseInt(val) : null
  } catch (e) { realGroupInput.value = original }
}
const groupFilterOptions = [
  { value: 'all', label: '全部' },
  { value: 'whitelist', label: '白名单' },
  { value: 'blacklist', label: '黑名单' },
  { value: 'active', label: '主动消息' },
  { value: 'function', label: '已开功能' }
]
const availableFunctionKeys = computed(() => {
  const keys = new Set()
  for (const key of knownFunctionKeys.value) {
    if (key) keys.add(key)
  }
  for (const config of Object.values(groupFunctionConfigs)) {
    for (const key of Object.keys(config || {})) {
      keys.add(key)
    }
  }
  return [...keys].sort((a, b) => a.localeCompare(b))
})
const addableFunctionKeys = computed(() => {
  const existing = new Set(funcEntries.value.map(([key]) => key))
  return availableFunctionKeys.value.filter(key => !existing.has(key))
})
const visibleGroups = computed(() => sortGroups(applyGroupFilter(groups.value)))
const filteredGroups = computed(() => {
  const q = groupSearch.value.toLowerCase()
  const filtered = q ? groups.value.filter(g => g.groupOpenId.toLowerCase().includes(q)) : groups.value
  return sortGroups(filtered)
})

function sortGroups(list) {
  return [...list].sort((a, b) => {
    if (a.whitelist !== b.whitelist) return a.whitelist ? -1 : 1
    return b.timestamp - a.timestamp
  })
}

function applyGroupFilter(list) {
  if (groupFilter.value === 'whitelist') return list.filter(g => g.whitelist)
  if (groupFilter.value === 'blacklist') return list.filter(g => g.blacklisted)
  if (groupFilter.value === 'active') return list.filter(g => g.allowedActive)
  if (groupFilter.value === 'function') {
    return list.filter(g => groupHasEnabledFunction(g.groupOpenId, functionFilterKey.value))
  }
  return list
}

function groupHasEnabledFunction(groupOpenId, functionKey) {
  const config = groupFunctionConfigs[groupOpenId]
  if (!config) return false
  if (functionKey) return !!config[functionKey]?.enabled
  return Object.values(config).some(val => val?.enabled)
}

function setGroupFilter(value) {
  groupFilter.value = value
  persistGroupFilter()
  if (value === 'function') {
    ensureGroupFunctionConfigs()
  }
}

function restoreGroupFilter() {
  try {
    const raw = localStorage.getItem(GROUP_FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (groupFilterOptions.some(option => option.value === saved?.type)) {
      groupFilter.value = saved.type
    }
    if (typeof saved?.functionKey === 'string') {
      functionFilterKey.value = saved.functionKey
    }
  } catch { /* ignore */ }
}

function persistGroupFilter() {
  try {
    localStorage.setItem(GROUP_FILTER_STORAGE_KEY, JSON.stringify({
      type: groupFilter.value,
      functionKey: functionFilterKey.value
    }))
  } catch { /* ignore */ }
}

function returnToGroupFilter() {
  selectedGroupId.value = ''
  closeDropdown()
  showInspector.value = !window.matchMedia('(max-width: 640px)').matches
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  funcEntries.value = []
  replyTo.value = null
  refMode.value = false
  if (groupFilter.value === 'function') {
    ensureGroupFunctionConfigs()
  }
}

watch(functionFilterKey, () => {
  persistGroupFilter()
  if (groupFilter.value === 'function') {
    ensureGroupFunctionConfigs()
  }
})

async function ensureGroupFunctionConfigs() {
  const missing = groups.value.filter(g => !groupFunctionConfigs[g.groupOpenId])
  if (!missing.length || functionFilterLoading.value) return
  functionFilterLoading.value = true
  try {
    await Promise.all(missing.map(async group => {
      try {
        const data = await api(`/groups/${encodeURIComponent(group.groupOpenId)}/functions`)
        groupFunctionConfigs[group.groupOpenId] = data || {}
      } catch {
        groupFunctionConfigs[group.groupOpenId] = {}
      }
    }))
  } finally {
    functionFilterLoading.value = false
  }
}

async function loadFunctionKeys() {
  try {
    const keys = await api('/groups/functions/keys')
    knownFunctionKeys.value = Array.isArray(keys) ? keys : []
  } catch {
    knownFunctionKeys.value = []
  }
}
const hasMore = computed(() => messages.value.length < totalMessages.value)
const orderedMessages = computed(() => [...messages.value].reverse())
const canSend = computed(() => {
  return !(!selectedGroupId.value || !draft.value.trim() || sending.value);

})

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  restoreGroupFilter()
  await loadConfig()
  await loadGroups()
  await loadFunctionKeys()
  const targetGroup = route.query.group
  if (targetGroup) {
    selectGroup(targetGroup)
  }
  connectSse()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  if (highlightTimer) clearTimeout(highlightTimer)
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
  const keyMap = { whitelist: 'whitelist', blacklist: 'blacklisted' }
  const key = keyMap[type]
  const old = selectedGroup.value[key]
  try {
    await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/${type}?enabled=${!old}`, { method: 'POST' })
    // 更新本地 selectedGroup
    const g = selectedGroup.value
    if (type === 'whitelist') g.whitelist = !old
    else if (type === 'blacklist') g.blacklisted = !old
  } catch (e) { notice.value = e.message }
}

async function toggleFunction(funcKey, enabled) {
  const key = funcKey?.trim()
  if (!selectedGroupId.value || !key) return false
  if (!availableFunctionKeys.value.includes(key)) {
    notice.value = '请选择已有功能配置'
    return false
  }
  try {
    await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/functions/${encodeURIComponent(key)}?enabled=${enabled}`, { method: 'POST' })
    // 更新本地
    const entry = funcEntries.value.find(([k]) => k === key)
    if (entry) {
      entry[1].enabled = enabled
    } else {
      funcEntries.value.push([key, { enabled }])
      funcEntries.value.sort(([a], [b]) => a.localeCompare(b))
    }
    if (!groupFunctionConfigs[selectedGroupId.value]) {
      groupFunctionConfigs[selectedGroupId.value] = {}
    }
    const config = groupFunctionConfigs[selectedGroupId.value]
    config[key] = { ...(config[key] || {}), enabled }
    if (!functionFilterKey.value && groupFilter.value === 'function') {
      functionFilterKey.value = key
    }
    return true
  } catch (e) {
    notice.value = e.message
    return false
  }
}

async function addFunctionKey() {
  const key = newFunctionKey.value
  if (!key) return
  if (await toggleFunction(key, true)) {
    newFunctionKey.value = ''
  }
}

async function loadGroupFunctions() {
  if (!selectedGroupId.value) return
  try {
    const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/functions`)
    funcEntries.value = data ? Object.entries(data) : []
    groupFunctionConfigs[selectedGroupId.value] = data || {}
  } catch { funcEntries.value = [] }
}

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function onDocumentClick(e) {
  if (!e.target.closest('.group-picker')) closeDropdown()
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
    pendingIsBlocked.value = data?.isBlocked || false
    pendingIsIgnored.value = data?.isIgnored || false
    pendingC2CPush.value = data?.c2cPush !== false
  } catch { pendingPermRole.value = 'USER'; pendingPermNodes.value = []; pendingIsBlocked.value = false; pendingIsIgnored.value = false; pendingC2CPush.value = true }
  newPermNode.value = ''
  showPermModal.value = true
}

async function confirmPermRole() {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/role?role=${pendingPermRole.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/blocked?value=${pendingIsBlocked.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/ignored?value=${pendingIsIgnored.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/push?value=${pendingC2CPush.value}`, { method: 'POST' })
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
  let payload
  try {
    payload = await response.json()
  } catch {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
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
  text = renderFaceTags(text)
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
    t = renderFaceTags(t)
    t = t.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
    t = t.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
    if (t.trim()) parts.push(renderMd(t))
  }
  if (ref.attachments && ref.attachments.length) {
    for (const a of ref.attachments) {
      if ((a.content_type || '').startsWith('image/') && a.url) {
        parts.push(`<img src="${a.url}" referrerpolicy="no-referrer" style="max-width:120px;max-height:80px;border-radius:4px;display:block" onerror="this.outerHTML='<span style=font-size:12px;color:#94a3b8>📷 图片</span>'">`)
      } else if (a.content_type === 'voice') {
        const text = a.asr_refer_text || a.filename || '语音消息'
        parts.push(`<span class="voice-ref">${escapeHtml(text)}</span>`)
      }
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
      attachments: (ref.attachments || []).filter(a => a.url || a.voice_wav_url)
    }
  } catch { return {} }
}

function getRefTargetMsgIdx(message) {
  if (!hasMsgRef(message)) return ''
  const fromRef = findRefIdx(message.messageReference)
  if (fromRef) return fromRef
  // 兼容旧版 WebUI 主动引用记录：当时把被引用消息的 msg_idx 暂存在本记录 refIdx。
  if ((message.eventType === 'BOT_SEND' || message.senderIsBot) && message.refIdx) {
    return message.refIdx
  }
  return ''
}

function findRefIdx(raw) {
  try {
    const value = typeof raw === 'string' ? JSON.parse(raw) : raw
    return findRefIdxValue(value)
  } catch {
    return ''
  }
}

function findRefIdxValue(value) {
  if (!value || typeof value !== 'object') return ''
  const keys = ['msg_idx', 'msgIdx', 'ref_idx', 'refIdx', 'message_id', 'messageId', 'msg_id', 'msgId']
  for (const key of keys) {
    const candidate = value[key]
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim()
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findRefIdxValue(item)
      if (found) return found
    }
  } else {
    for (const item of Object.values(value)) {
      const found = findRefIdxValue(item)
      if (found) return found
    }
  }
  return ''
}

async function jumpToReference(message) {
  const msgIdx = getRefTargetMsgIdx(message)
  const ref = parseMsgRef(message.messageReference)
  if (!selectedGroupId.value) return

  const loaded = findLoadedReference(message, msgIdx)
  if (loaded) {
    await nextTick()
    highlightMessage(loaded.id)
    return
  }

  if (!msgIdx && !ref.content && !(ref.attachments && ref.attachments.length)) {
    notice.value = '引用来源消息缺少可定位信息'
    return
  }

  try {
    const params = new URLSearchParams({
      pageSize: String(pageSize),
      excludeId: String(message.id)
    })
    if (msgIdx) params.set('msgIdx', msgIdx)
    if (ref.author) params.set('refAuthor', ref.author)
    if (ref.content) params.set('refContent', ref.content)
    if (ref.attachments && ref.attachments.length) {
      params.set('refAttachments', JSON.stringify(ref.attachments))
    }
    const location = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/messages/ref?${params}`)
    const page = location.page || 1
    const data = await api(`/groups/${encodeURIComponent(selectedGroupId.value)}/messages?page=${page}&pageSize=${pageSize}`)
    messages.value = data.records || []
    totalMessages.value = data.total || totalMessages.value
    currentPage.value = page
    await nextTick()
    highlightMessage(location.record.id)
  } catch (error) {
    notice.value = error.message
  }
}

function findLoadedReference(message, refIdx) {
  if (refIdx) {
    const byId = messages.value.find(m =>
      m.id !== message.id && m.refIdx === refIdx
    )
    if (byId) return byId
  }

  const ref = parseMsgRef(message.messageReference)
  const refText = normalizeRefText(ref.content)
  const refAuthor = (ref.author || '').trim()
  const candidates = messages.value
    .filter(m => m.id !== message.id)
    .sort((a, b) => {
      const aOlder = a.id < message.id ? 0 : 1
      const bOlder = b.id < message.id ? 0 : 1
      if (aOlder !== bOlder) return aOlder - bOlder
      return Math.abs(message.id - a.id) - Math.abs(message.id - b.id)
    })

  if (refText) {
    const exact = candidates.find(m =>
      (!refAuthor || m.username === refAuthor) && normalizeRefText(m.content) === refText
    )
    if (exact) return exact

    if (refText.length >= 6) {
      const partial = candidates.find(m => {
        if (refAuthor && m.username !== refAuthor) return false
        const content = normalizeRefText(m.content)
        return !!content && (content.includes(refText) || refText.includes(content))
      })
      if (partial) return partial
    }
  }

  const refAttachments = ref.attachments || []
  if (refAttachments.length) {
    const byAttachment = candidates.find(m => {
      if (refAuthor && m.username !== refAuthor) return false
      const attachments = parseRawAttachments(m.attachments)
      return refAttachments.some(refAtt => attachments.some(att => sameAttachment(att, refAtt)))
    })
    if (byAttachment) return byAttachment
  }

  return null
}

function normalizeRefText(text) {
  let value = renderFaceTags(text || '')
  value = value.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  value = value.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  value = value.replace(/(<@[A-F0-9]+>)\s+\1/g, '$1')
  return value.trim()
}

function parseRawAttachments(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function sameAttachment(a, b) {
  if (!a || !b) return false
  if (a.url && b.url && a.url === b.url) return true
  if (a.voice_wav_url && b.voice_wav_url && a.voice_wav_url === b.voice_wav_url) return true
  if (a.filename && b.filename && a.filename === b.filename) return true
  return false
}

function highlightMessage(id) {
  highlightedMessageId.value = id
  const el = messageListRef.value?.querySelector(`[data-message-id="${id}"]`)
  if (el) {
    el.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }
  if (highlightTimer) clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    highlightedMessageId.value = null
  }, 1800)
}

function parseAttach(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr.map(normalizeAttachment).filter(Boolean)
  } catch { return [] }
}

function normalizeAttachment(att) {
  const contentType = att?.content_type || ''
  if (att?.url && contentType.startsWith('image/')) {
    return { ...att, type: 'image' }
  }
  if (contentType === 'voice') {
    return {
      ...att,
      type: 'voice',
      asrText: att.asr_refer_text || '',
      voiceUrl: att.voice_wav_url || ''
    }
  }
  return null
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
    if (groupFilter.value === 'function') {
      ensureGroupFunctionConfigs()
    }
    notice.value = `已加载 ${groups.value.length} 个群`
  } catch (error) {
    notice.value = error.message
  } finally {
    loadingGroups.value = false
  }
}

function toggleDropdown() {
  if (dropdownOpen.value) {
    closeDropdown()
  } else {
    dropdownOpen.value = true
    nextTick(() => {
      const el = dropdownRef.value
      if (el) el.scrollTop = dropdownScrollTop.value
    })
  }
}

function closeDropdown() {
  if (!dropdownOpen.value) return
  const el = dropdownRef.value
  if (el) dropdownScrollTop.value = el.scrollTop
  dropdownOpen.value = false
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
