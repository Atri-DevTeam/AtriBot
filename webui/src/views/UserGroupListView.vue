<template>
  <div class="shell userlist-page">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="refreshAll">刷新</button>
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
          <h2 class="feedback-title">用户数据</h2>
        </div>
      </header>

      <section class="content userlist-layout">
        <section class="chat-panel userlist-panel dir-panel">
          <div class="chat-head dir-chat-head">
            <div class="userlist-source-tabs" role="tablist">
              <button v-for="tab in tabs" :key="tab.key"
                      class="userlist-source-tab" :class="{ active: activeTab === tab.key }"
                      type="button" role="tab" :aria-selected="activeTab === tab.key"
                      @click="switchTab(tab.key)">{{ tab.label }}</button>
            </div>
            <div class="dir-head-right">
              <button v-if="activeTab === 'groups'" type="button" class="broadcast-trigger" :disabled="loadingGroups"
                      aria-label="群广播" title="群广播" @click="openBroadcast">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M4 10h3l9-4v13l-9-4H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1Z" />
                  <path d="m7 15 1.3 4h3.2l-1.4-4.7" />
                  <path d="M19 9a4 4 0 0 1 0 6" />
                </svg>
              </button>
              <button ref="filterBtn" type="button" class="dir-filter-btn" :class="{ active: filterOpen }" @click="toggleFilter">
                <svg viewBox="0 0 24 24"><path d="M22 3H2l8 9.46V19l4 2v-8.54z"/></svg>
                筛选
              </button>
              <span class="status-pill">
                <span class="dot ok"></span>{{ activeTab === 'groups' ? `${groups.length} 个群` : `${users.length} 个用户` }}
              </span>
            </div>
          </div>

          <div class="userlist-search-bar">
            <input
              v-model="searchText"
              class="userlist-search-input"
              type="text"
              :placeholder="activeTab === 'groups' ? '搜索群信息...' : '搜索用户信息...'"
              @keyup.enter="applySearch"
            />
            <button class="primary-button" @click="applySearch">搜索</button>
            <button v-if="searchText" class="ghost-button" @click="clearSearch">清除</button>
          </div>

          <div class="userlist-content dir-content">
            <!-- ═══════ 群列表 ═══════ -->
            <template v-if="activeTab === 'groups'">
              <div v-if="loadingGroups" class="empty-state">加载中...</div>
              <div v-else-if="groupsError" class="empty-state error">{{ groupsError }}</div>
              <div v-else-if="groups.length === 0" class="empty-state">暂无群数据</div>
              <div v-else-if="visibleGroups.length === 0" class="empty-state">没有匹配的群</div>
              <div v-else class="dir-grid">
                <article v-for="g in visibleGroups" :key="g.groupOpenId" class="dir-tile">
                  <div class="dir-tile-head">
                    <span class="dir-avatar-sm" :style="tileStyle(g.groupOpenId)">{{ groupInitial(g) }}</span>
                    <span class="dir-tile-name" :title="groupName(g)">{{ groupName(g) }}</span>
                    <div class="dir-tile-actions">
                      <button class="dir-mini-btn" title="进入聊天" @click="openGroupChat(g.groupOpenId)">
                        <svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      </button>
                      <button class="dir-mini-btn" title="复制群ID" @click="copyText(g.groupOpenId)">
                        <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                      </button>
                    </div>
                  </div>
                  <div class="dir-tile-line">
                    <span class="dir-tile-mono">GID {{ shortId(g.groupOpenId) }}</span>
                    <span v-if="g.realGroupId">群号 {{ g.realGroupId }}</span>
                    <span>成员 {{ g.groupMemberNum || '-' }}</span>
                    <span v-if="g.joinedAt">{{ formatTime(g.joinedAt) }}</span>
                  </div>
                  <div class="dir-tile-line">
                    <span v-if="g.memberRole" class="dir-role-chip" :class="gRoleCls(g.memberRole)">{{ gRoleLabel(g.memberRole) }}</span>
                    <span class="dir-chips">
                      <span v-if="g.whitelist" class="dir-chip dir-chip-green">白名单</span>
                      <span v-if="g.blacklisted" class="dir-chip dir-chip-red">黑名单</span>
                      <span v-if="g.allowProactiveMsg" class="dir-chip dir-chip-blue">主动</span>
                    </span>
                    <span v-if="groupTypeText(g) !== '-'" class="dir-tile-type" :title="groupTypeText(g)">{{ groupTypeText(g) }}</span>
                  </div>
                </article>
              </div>
            </template>

            <!-- ═══════ 用户列表 ═══════ -->
            <template v-else>
              <div v-if="loadingUsers" class="empty-state">加载中...</div>
              <div v-else-if="usersError" class="empty-state error">{{ usersError }}</div>
              <div v-else-if="users.length === 0" class="empty-state">暂无用户数据</div>
              <div v-else-if="visibleUsers.length === 0" class="empty-state">没有匹配的用户</div>
              <div v-else class="dir-grid">
                <article v-for="u in visibleUsers" :key="u.userOpenId" class="dir-tile">
                  <div class="dir-tile-head">
                    <span class="dir-avatar-sm circle" :style="tileStyle(u.userOpenId)">
                      <span>{{ userInitial(u) }}</span>
                      <img v-if="appId" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${u.userOpenId}/100`"
                           alt="" referrerpolicy="no-referrer" loading="lazy"
                           @error="$event.target.style.display='none'" />
                    </span>
                    <span class="dir-tile-name" :title="u.username || u.userOpenId">{{ u.username || u.userOpenId }}</span>
                    <div class="dir-tile-actions">
                      <button class="dir-mini-btn" title="更改信息" @click="openPermModal(u.userOpenId)">
                        <span style="font-size:13px;line-height:1">⚙</span>
                      </button>
                      <button class="dir-mini-btn" title="进入私聊" @click="openUserChat(u)">
                        <svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><circle cx="12" cy="10" r="1.1" fill="currentColor" stroke="none"/></svg>
                      </button>
                      <button class="dir-mini-btn" title="复制用户ID" @click="copyText(u.userOpenId)">
                        <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                      </button>
                    </div>
                  </div>
                  <div class="dir-tile-line">
                    <span class="dir-tile-mono">UID {{ shortId(u.userOpenId) }}</span>
                    <span>权限 {{ (u.permissions || []).length }} 项</span>
                  </div>
                  <div class="dir-tile-line">
                    <span v-if="u.role" class="dir-role-chip" :class="uRoleCls(u.role)">{{ uRoleLabel(u.role) }}</span>
                    <span class="dir-chips">
                      <span v-if="u.isBlocked" class="dir-chip dir-chip-red">拉黑</span>
                      <span v-if="u.isIgnored" class="dir-chip dir-chip-gray">屏蔽</span>
                      <span v-if="u.c2cPush" class="dir-chip dir-chip-blue">主动推送</span>
                    </span>
                  </div>
                </article>
              </div>
            </template>
          </div>
        </section>
      </section>
    </main>

    <!-- 更改信息弹窗 -->
    <div v-if="showPermModal" class="modal-backdrop" @click="showPermModal = false">
      <div class="modal" @click.stop>
        <div class="modal-head">
          <h2>更改信息</h2>
          <button class="icon-button" @click="showPermModal = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
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
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="showPermModal = false">关闭</button>
          <button class="primary-button" @click="confirmPerm(); showPermModal = false">确认</button>
        </div>
      </div>
    </div>

    <div v-if="showBroadcast" class="modal-backdrop broadcast-backdrop"
         :aria-hidden="broadcastPickerOpen ? 'true' : undefined" @click.self="closeBroadcast">
      <section class="modal broadcast-modal" role="dialog" aria-modal="true" aria-labelledby="broadcast-title">
        <div class="modal-head">
          <h2 id="broadcast-title">群广播 · 主动消息</h2>
          <button type="button" class="icon-button" aria-label="关闭广播" :disabled="broadcastSending" @click="closeBroadcast">×</button>
        </div>
        <div class="modal-body broadcast-body">
          <label class="broadcast-field">
            <span>目标分类</span>
            <select v-model="broadcastCategory" :disabled="broadcastSending" @change="broadcastConfirm = false">
              <option v-for="option in broadcastCategories" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <div v-if="broadcastCategory === 'custom'" class="broadcast-picker">
            <span>已选 {{ broadcastSelectedGroups.length }} 个群</span>
            <button type="button" class="ghost-button" :disabled="broadcastSending"
                    @click="openBroadcastPicker">{{ broadcastSelectedGroups.length ? '重新选择群' : '打开选群面板' }}</button>
          </div>
          <p class="broadcast-note">仅发送给当前允许主动消息、且未被拉黑的群。发送前会显示实际目标；发送时请保持页面打开，期间可停止后续发送。</p>
          <div class="broadcast-targets">
            <strong>目标群 {{ broadcastTargets.length }} 个</strong>
            <div v-if="broadcastTargets.length" class="broadcast-target-list">
              <span v-for="group in broadcastTargets" :key="group.groupOpenId" :title="group.groupOpenId">{{ groupName(group) }}</span>
            </div>
            <span v-else class="broadcast-note">{{ broadcastCategory === 'custom' ? '请先勾选目标群' : '该分类暂无可发送的群' }}</span>
          </div>
          <div class="broadcast-types" role="group" aria-label="广播类型">
            <label v-for="type in broadcastTypes" :key="type.value" :class="{ active: broadcastType === type.value }">
              <input v-model="broadcastType" type="radio" :value="type.value" :disabled="broadcastSending" @change="broadcastConfirm = false" />
              {{ type.label }}
            </label>
          </div>
          <template v-if="broadcastType === 'ark'">
            <label class="broadcast-field"><span>卡片描述</span><input v-model="broadcastArk.description" :disabled="broadcastSending" placeholder="卡片描述" @input="broadcastConfirm = false" /></label>
            <label class="broadcast-field"><span>通知预览</span><input v-model="broadcastArk.prompt" :disabled="broadcastSending" placeholder="消息列表和通知中显示的文字" @input="broadcastConfirm = false" /></label>
            <div v-for="(item, index) in broadcastArk.items" :key="index" class="broadcast-ark-item">
              <label class="broadcast-field"><span>条目 {{ index + 1 }}</span><input v-model="item.description" :disabled="broadcastSending" placeholder="条目内容" @input="broadcastConfirm = false" /></label>
              <label class="broadcast-field"><span>链接（可选）</span><input v-model="item.link" :disabled="broadcastSending" placeholder="https://…" @input="broadcastConfirm = false" /></label>
              <button type="button" class="ghost-button" :disabled="broadcastSending || broadcastArk.items.length === 1" @click="broadcastArk.items.splice(index, 1); broadcastConfirm = false">删除</button>
            </div>
            <button type="button" class="ghost-button" :disabled="broadcastSending" @click="broadcastArk.items.push({ description: '', link: '' }); broadcastConfirm = false">添加条目</button>
          </template>
          <template v-else>
            <label v-if="broadcastType === 'image'" class="broadcast-field">
              <span>图片</span>
              <input type="file" accept="image/*" :disabled="broadcastSending" @change="pickBroadcastImage" />
            </label>
            <img v-if="broadcastType === 'image' && broadcastImagePreview" class="broadcast-image-preview" :src="broadcastImagePreview" alt="待发送图片预览" />
            <label class="broadcast-field">
              <span>{{ broadcastType === 'image' ? '图片说明（可选）' : broadcastType === 'markdown' ? 'Markdown 内容' : '文本内容' }}</span>
              <textarea v-model="broadcastText" rows="5" :disabled="broadcastSending"
                        :placeholder="broadcastType === 'image' ? '可选的图片说明' : '输入广播内容'"
                        @input="broadcastConfirm = false" @paste="pasteBroadcastImage" />
            </label>
          </template>
          <p v-if="broadcastConfirm && !broadcastSending" class="broadcast-confirm" role="alert">
            确认向以上 {{ broadcastTargets.length }} 个群发送{{ broadcastTypeLabel }}广播？发送后无法批量撤回。
          </p>
          <div v-if="broadcastProgress" class="broadcast-progress" role="status">
            已处理 {{ broadcastProgress.done }}/{{ broadcastProgress.total }} 个群；成功 {{ broadcastProgress.success }}，失败 {{ broadcastProgress.failures.length }}。
            <span v-if="broadcastProgress.stopped">已停止后续发送。</span>
            <span v-else-if="!broadcastSending">广播已完成。</span>
          </div>
          <ul v-if="broadcastProgress?.failures.length" class="broadcast-failures">
            <li v-for="failure in broadcastProgress.failures" :key="failure.id">{{ failure.name }}：{{ failure.reason }}</li>
          </ul>
        </div>
        <div class="modal-foot">
          <button v-if="broadcastSending" type="button" class="ghost-button" :disabled="broadcastStop" @click="broadcastStop = true">{{ broadcastStop ? '正在停止…' : '停止后续发送' }}</button>
          <template v-else>
            <button type="button" class="ghost-button" @click="closeBroadcast">关闭</button>
            <button v-if="!broadcastProgress && !broadcastConfirm" type="button" class="primary-button" :disabled="!canBroadcast" @click="broadcastConfirm = true">核对并发送</button>
            <button v-else-if="!broadcastProgress" type="button" class="primary-button" :disabled="!canBroadcast" @click="sendBroadcast">确认发送 {{ broadcastTargets.length }} 个群</button>
          </template>
        </div>
      </section>
    </div>

    <div v-if="showBroadcast && broadcastPickerOpen" class="modal-backdrop broadcast-picker-overlay"
         @click.self="closeBroadcastPicker" @keydown.esc="closeBroadcastPicker">
      <section class="modal broadcast-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="broadcast-picker-title">
        <div class="modal-head broadcast-picker-head">
          <div>
            <h2 id="broadcast-picker-title">选择广播群</h2>
            <p>从 {{ broadcastEligibleGroups.length }} 个可主动发送的群中选择</p>
          </div>
          <button type="button" class="icon-button" aria-label="关闭选群面板" @click="closeBroadcastPicker">×</button>
        </div>
        <div class="broadcast-picker-layout">
          <div class="broadcast-picker-main">
            <div class="broadcast-picker-controls">
              <input ref="broadcastPickerSearchRef" v-model="broadcastGroupSearch" type="search"
                     placeholder="搜索群名称、ID、群号、分类或标签" aria-label="搜索自选群" />
              <select v-model="broadcastPickerFilter" aria-label="筛选可选群">
                <option v-for="option in broadcastPickerFilters" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="broadcast-picker-actions">
              <span>找到 {{ broadcastPickerGroups.length }} 个群</span>
              <div>
                <button type="button" class="ghost-button" :disabled="!broadcastPickerGroups.length"
                        @click="selectVisibleBroadcastGroups">全选当前结果</button>
                <button type="button" class="ghost-button" :disabled="!broadcastPickerGroups.length"
                        @click="deselectVisibleBroadcastGroups">取消当前结果</button>
              </div>
            </div>
            <div class="broadcast-picker-list" role="group" aria-label="自选群列表">
              <label v-for="group in broadcastPickerGroups" :key="group.groupOpenId" class="broadcast-picker-row">
                <input type="checkbox" :checked="broadcastSelectedIds.includes(group.groupOpenId)"
                       @change="toggleBroadcastGroup(group.groupOpenId, $event.target.checked)" />
                <span class="broadcast-picker-name">{{ groupName(group) }}</span>
                <span class="broadcast-picker-meta">{{ groupTypeText(group) }} · {{ group.realGroupId || shortId(group.groupOpenId) }}</span>
              </label>
              <p v-if="!broadcastPickerGroups.length" class="broadcast-note">没有匹配的可发送群</p>
            </div>
          </div>
          <aside class="broadcast-picker-selected">
            <div class="broadcast-picker-selected-head">
              <strong>已选 {{ broadcastSelectedGroups.length }} 个</strong>
              <button type="button" class="ghost-button" :disabled="!broadcastSelectedGroups.length"
                      @click="clearBroadcastGroups">清空</button>
            </div>
            <div class="broadcast-picker-selected-list">
              <div v-for="group in broadcastSelectedGroups" :key="group.groupOpenId" class="broadcast-picker-selected-row">
                <span :title="group.groupOpenId">{{ groupName(group) }}</span>
                <button type="button" :aria-label="'移除 ' + groupName(group)"
                        @click="toggleBroadcastGroup(group.groupOpenId, false)">×</button>
              </div>
              <p v-if="!broadcastSelectedGroups.length" class="broadcast-note">尚未选择群</p>
            </div>
          </aside>
        </div>
        <div class="modal-foot broadcast-picker-foot">
          <span>只显示允许主动消息、且未被拉黑的群</span>
          <button type="button" class="primary-button" @click="closeBroadcastPicker">完成选择</button>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <div v-if="filterOpen" class="dir-filter-backdrop" @click="filterOpen = false"></div>
      <div v-if="filterOpen" class="dir-filter-panel userlist-filter-theme" :style="filterPanelStyle">
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">分类</span>
          <select v-model="groupFilterValue" class="dir-filter-select" title="群分类筛选">
            <option v-for="opt in groupFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">消息类型</span>
          <select v-model="groupMsgValue" class="dir-filter-select" title="按消息类型筛选">
            <option v-for="opt in groupMsgOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">功能</span>
          <select v-model="groupFuncValue" class="dir-filter-select" title="按功能筛选">
            <option v-for="opt in groupFuncOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-else class="dir-filter-field">
          <span class="dir-filter-field-label">身份</span>
          <select v-model="userFilterValue" class="dir-filter-select" title="按身份筛选">
            <option v-for="opt in userFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import '../styles/userlist-theme.css'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

/* ═══════════ 双 Tab：群列表 / 用户列表 ═══════════ */

const tabs = [
  { key: 'groups', label: '群列表' },
  { key: 'users', label: '用户列表' }
]
const activeTab = ref('groups')

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  searchText.value = ''
  currentSearch.value = ''
}

/* ═══════════ 数据 ═══════════ */

const groups = ref([])
const loadingGroups = ref(false)
const groupsError = ref('')

const users = ref([])
const loadingUsers = ref(false)
const usersError = ref('')

const loading = computed(() => loadingGroups.value || loadingUsers.value)

const searchText = ref('')
const currentSearch = ref('')

const groupFilterValue = ref('')
const groupMsgValue = ref('')
const groupFuncValue = ref('')
const userFilterValue = ref('')

/* ═══════════ 筛选二级菜单 ═══════════ */

const filterOpen = ref(false)
const filterBtn = ref(null)
const filterPanelStyle = ref({})

function toggleFilter() {
  filterOpen.value = !filterOpen.value
  if (filterOpen.value && filterBtn.value) {
    const r = filterBtn.value.getBoundingClientRect()
    const vw = window.innerWidth
    filterPanelStyle.value = vw <= 640
      ? { top: r.bottom + 6 + 'px', left: '8px', right: '8px' }
      : { top: r.bottom + 6 + 'px', right: Math.max(8, vw - r.right) + 'px' }
  }
}

const groupFilterOptions = [
  { value: '', label: '全部分类' },
  { value: 'whitelist', label: '白名单' },
  { value: 'blacklist', label: '黑名单' },
  { value: 'admin', label: '是管理员' },
  { value: 'active', label: '主动消息' }
]
const groupMsgOptions = [
  { value: '', label: '全部' },
  { value: 'only_mention', label: '仅@' },
  { value: 'mention_and_context', label: '@+上下文' },
  { value: 'all', label: '全部消息' }
]
const userFilterOptions = [
  { value: '', label: '全部身份' },
  { value: 'owner', label: '开发' },
  { value: 'admin', label: '管理' },
  { value: 'user', label: '用户' }
]

const groupFuncKeys = ref([])
const groupFuncOptions = computed(() => [
  { value: '', label: '全部功能' },
  ...groupFuncKeys.value.map(k => ({ value: k, label: k }))
])

/* ═══════════ 群广播（主动消息） ═══════════ */

const broadcastTypes = [
  { value: 'text', label: '文本' },
  { value: 'markdown', label: 'MD' },
  { value: 'image', label: '图片' },
  { value: 'ark', label: 'Ark' }
]
const showBroadcast = ref(false)
const broadcastCategory = ref('all')
const broadcastPickerOpen = ref(false)
const broadcastPickerSearchRef = ref(null)
const broadcastPickerFilter = ref('all')
const broadcastGroupSearch = ref('')
const broadcastSelectedIds = ref([])
const broadcastType = ref('text')
const broadcastText = ref('')
const broadcastArk = reactive({ description: '', prompt: '', items: [{ description: '', link: '' }] })
const broadcastImage = ref(null)
const broadcastImagePreview = ref(null)
const broadcastConfirm = ref(false)
const broadcastSending = ref(false)
const broadcastStop = ref(false)
const broadcastProgress = ref(null)

const broadcastEligibleGroups = computed(() => groups.value.filter(group =>
  group.groupOpenId && group.allowProactiveMsg && !group.blacklisted
))
const broadcastCategories = computed(() => {
  const classes = [...new Set(broadcastEligibleGroups.value.map(group => group.groupClassText?.trim()).filter(Boolean))].sort()
  const tags = [...new Set(broadcastEligibleGroups.value.flatMap(group => group.groupTags || []).map(tag => tag?.trim()).filter(Boolean))].sort()
  return [
    { value: 'all', label: '全部可主动发送的群' },
    { value: 'custom', label: '自选群' },
    { value: 'whitelist', label: '白名单群' },
    { value: 'admin', label: '机器人是群主或管理员' },
    ...classes.map(value => ({ value: 'class:' + value, label: '群分类 · ' + value })),
    ...tags.map(value => ({ value: 'tag:' + value, label: '群标签 · ' + value })),
    ...groupFuncKeys.value.map(value => ({ value: 'function:' + value, label: '已启用功能 · ' + value }))
  ]
})
const broadcastPickerFilters = computed(() => broadcastCategories.value
  .filter(option => option.value !== 'custom')
  .map(option => option.value === 'all' ? { value: 'all', label: '全部可选群' } : option))
const broadcastSelectedGroups = computed(() => {
  const byId = new Map(broadcastEligibleGroups.value.map(group => [group.groupOpenId, group]))
  return broadcastSelectedIds.value.map(id => byId.get(id)).filter(Boolean)
})
const broadcastPickerGroups = computed(() => {
  const query = broadcastGroupSearch.value.trim().toLowerCase()
  const list = broadcastEligibleGroups.value.filter(group =>
    groupMatchesBroadcastCategory(group, broadcastPickerFilter.value)
    && (!query || [groupName(group), group.groupOpenId, group.realGroupId, group.groupClassText, ...(group.groupTags || [])]
      .some(value => String(value || '').toLowerCase().includes(query)))
  )
  return [...list].sort((a, b) => groupName(a).localeCompare(groupName(b), 'zh-CN'))
})
function groupMatchesBroadcastCategory(group, category) {
  if (category === 'all') return true
  if (category === 'custom') return broadcastSelectedIds.value.includes(group.groupOpenId)
  if (category === 'whitelist') return group.whitelist
  if (category === 'admin') return ['owner', 'admin', 'administrator'].includes((group.memberRole || '').toLowerCase())
  if (category.startsWith('class:')) return group.groupClassText?.trim() === category.slice(6)
  if (category.startsWith('tag:')) return (group.groupTags || []).some(tag => tag?.trim() === category.slice(4))
  if (category.startsWith('function:')) return (group.enabledFunctions || []).includes(category.slice(9))
  return false
}
const broadcastTargets = computed(() => broadcastEligibleGroups.value.filter(group =>
  groupMatchesBroadcastCategory(group, broadcastCategory.value)
))
const broadcastTypeLabel = computed(() => broadcastTypes.find(type => type.value === broadcastType.value)?.label || '')
const canBroadcast = computed(() => {
  if (broadcastSending.value || !broadcastTargets.value.length) return false
  if (broadcastType.value === 'image') return !!broadcastImage.value
  if (broadcastType.value === 'ark') {
    return !!broadcastArk.description.trim() && !!broadcastArk.prompt.trim()
      && broadcastArk.items.length > 0 && broadcastArk.items.every(item => !!item.description.trim())
  }
  return !!broadcastText.value.trim()
})

function toggleBroadcastGroup(groupOpenId, checked) {
  broadcastSelectedIds.value = checked
    ? [...new Set([...broadcastSelectedIds.value, groupOpenId])]
    : broadcastSelectedIds.value.filter(id => id !== groupOpenId)
  broadcastConfirm.value = false
}

function selectVisibleBroadcastGroups() {
  broadcastSelectedIds.value = [...new Set([
    ...broadcastSelectedIds.value, ...broadcastPickerGroups.value.map(group => group.groupOpenId)
  ])]
  broadcastConfirm.value = false
}

function deselectVisibleBroadcastGroups() {
  const visibleIds = new Set(broadcastPickerGroups.value.map(group => group.groupOpenId))
  broadcastSelectedIds.value = broadcastSelectedIds.value.filter(id => !visibleIds.has(id))
  broadcastConfirm.value = false
}

function clearBroadcastGroups() {
  broadcastSelectedIds.value = []
  broadcastConfirm.value = false
}

async function openBroadcastPicker() {
  broadcastPickerFilter.value = 'all'
  broadcastGroupSearch.value = ''
  broadcastPickerOpen.value = true
  await nextTick()
  broadcastPickerSearchRef.value?.focus()
}

function closeBroadcastPicker() {
  broadcastPickerOpen.value = false
}

async function openBroadcast() {
  await Promise.all([loadGroups(), loadGroupFuncKeys()])
  if (groupsError.value) return
  broadcastCategory.value = 'all'
  broadcastPickerOpen.value = false
  broadcastPickerFilter.value = 'all'
  broadcastGroupSearch.value = ''
  broadcastSelectedIds.value = []
  broadcastType.value = 'text'
  broadcastText.value = ''
  broadcastArk.description = ''
  broadcastArk.prompt = ''
  broadcastArk.items = [{ description: '', link: '' }]
  broadcastImage.value = null
  broadcastImagePreview.value = null
  broadcastConfirm.value = false
  broadcastProgress.value = null
  broadcastStop.value = false
  showBroadcast.value = true
}

function closeBroadcast() {
  if (broadcastSending.value) return
  broadcastPickerOpen.value = false
  showBroadcast.value = false
}

function readBroadcastImage(file) {
  if (!file || !file.type.startsWith('image/')) return
  broadcastConfirm.value = false
  broadcastImage.value = null
  broadcastImagePreview.value = null
  const reader = new FileReader()
  reader.onload = () => {
    if (typeof reader.result !== 'string') return
    broadcastImage.value = reader.result.split(',')[1]
    broadcastImagePreview.value = reader.result
  }
  reader.readAsDataURL(file)
}

function pickBroadcastImage(event) {
  readBroadcastImage(event.target.files?.[0])
  event.target.value = ''
}

function pasteBroadcastImage(event) {
  if (broadcastType.value !== 'image') return
  const item = [...(event.clipboardData?.items || [])].find(item => item.type.startsWith('image/'))
  if (!item) return
  event.preventDefault()
  readBroadcastImage(item.getAsFile())
}

function broadcastBody() {
  const body = { msgType: broadcastType.value, content: broadcastText.value.trim() }
  if (body.msgType === 'markdown') {
    body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
  } else if (body.msgType === 'image') {
    body.imageType = 'base64'
    body.imageValue = broadcastImage.value
  } else if (body.msgType === 'ark') {
    delete body.content
    body.ark = {
      description: broadcastArk.description.trim(),
      prompt: broadcastArk.prompt.trim(),
      items: broadcastArk.items.map(item => ({ description: item.description.trim(), link: item.link.trim() || null }))
    }
  }
  return body
}

async function sendBroadcast() {
  if (!broadcastConfirm.value || !canBroadcast.value) return
  const targets = [...broadcastTargets.value]
  const body = broadcastBody()
  broadcastConfirm.value = false
  broadcastSending.value = true
  broadcastStop.value = false
  broadcastProgress.value = { total: targets.length, done: 0, success: 0, failures: [], stopped: false }
  try {
    for (const group of targets) {
      if (broadcastStop.value) break
      try {
        await api('/groups/send', { method: 'POST', body: JSON.stringify({ ...body, groupOpenId: group.groupOpenId }) })
        broadcastProgress.value.success++
      } catch (error) {
        broadcastProgress.value.failures.push({
          id: group.groupOpenId, name: groupName(group), reason: error.message || '发送失败'
        })
        if (error.message === '未授权' || error.message === 'WebUI 已关闭'
          || /频控|频率限制|rate.?limit/i.test(error.message || '')) broadcastStop.value = true
      }
      broadcastProgress.value.done++
      if (!broadcastStop.value && broadcastProgress.value.done < targets.length) {
        await new Promise(resolve => setTimeout(resolve, 1200))
      }
    }
    broadcastProgress.value.stopped = broadcastStop.value
  } finally {
    broadcastSending.value = false
  }
}

/* ═══════════ 群列表筛选/排序（全量本地） ═══════════ */

const visibleGroups = computed(() => {
  let list = groups.value
  if (groupFilterValue.value === 'whitelist') list = list.filter(g => g.whitelist)
  else if (groupFilterValue.value === 'blacklist') list = list.filter(g => g.blacklisted)
  else if (groupFilterValue.value === 'admin') {
    // memberRole 是机器人在群内的身份，群主也算管理
    list = list.filter(g => ['owner', 'admin', 'administrator'].includes((g.memberRole || '').toLowerCase()))
  }
  else if (groupFilterValue.value === 'active') list = list.filter(g => g.allowProactiveMsg)
  if (groupMsgValue.value) {
    list = list.filter(g => (g.recvMsgSetting || '') === groupMsgValue.value)
  }
  if (groupFuncValue.value) {
    list = list.filter(g => (g.enabledFunctions || []).includes(groupFuncValue.value))
  }
  const q = currentSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(g =>
      (g.groupOpenId || '').toLowerCase().includes(q) ||
      (g.groupName || '').toLowerCase().includes(q) ||
      (g.realGroupId != null && String(g.realGroupId).includes(q))
    )
  }
  return [...list].sort((a, b) => {
    if (a.whitelist !== b.whitelist) return a.whitelist ? -1 : 1
    return parseTime(b.joinedAt) - parseTime(a.joinedAt)
  })
})

function groupName(g) {
  return g.groupName || g.groupOpenId || '未命名群'
}

function groupInitial(g) {
  return (groupName(g) || '?').slice(0, 1).toUpperCase()
}

function groupTypeText(g) {
  return [g.groupClassText, ...(g.groupTags || [])].filter(Boolean).join(' · ') || '-'
}

/* ═══════════ 用户列表筛选/排序（全量本地） ═══════════ */

const visibleUsers = computed(() => {
  let list = users.value
  const r = userFilterValue.value
  if (r) {
    list = list.filter(u => {
      const role = (u.role || '').toLowerCase()
      if (r === 'owner') return role === 'owner'
      if (r === 'admin') return role === 'admin'
      if (r === 'user') return role === 'user'
      return true
    })
  }
  const q = currentSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(u =>
      (u.userOpenId || '').toLowerCase().includes(q) ||
      (u.username || '').toLowerCase().includes(q)
    )
  }
  return [...list].sort((a, b) =>
    (a.username || a.userOpenId || '').localeCompare(b.username || b.userOpenId || '')
  )
})

function userInitial(u) {
  const v = u.username || u.userOpenId || '?'
  return v.slice(0, 1).toUpperCase()
}

/* ═══════════ 身份徽标 ── */

function gRoleCls(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'owner'
  if (r === 'admin' || r === 'administrator') return 'admin'
  return 'member'
}

function gRoleLabel(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return '群主'
  if (r === 'admin' || r === 'administrator') return '管理员'
  if (r === 'member') return '成员'
  return role
}

function uRoleCls(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'owner'
  if (r === 'admin' || r === 'administrator') return 'admin'
  return 'user'
}

function uRoleLabel(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return '开发'
  if (r === 'admin' || r === 'administrator') return '管理'
  return '用户'
}

/* ═══════════ 工具 ═══════════ */

function shortId(id) {
  if (!id) return '-'
  return id.length > 16 ? `${id.slice(0, 16)}…` : id
}

function tileStyle(openId) {
  let h = 0
  for (const ch of openId || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return { background: `hsl(${h % 360}, 42%, 62%)` }
}

// joinedAt 存的是秒级时间戳，统一转毫秒
function parseTime(value) {
  if (!value) return 0
  const raw = String(value)
  if (/^\d+$/.test(raw.trim())) {
    const n = Number(raw.trim())
    return n < 1e12 ? n * 1000 : n
  }
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function formatTime(value) {
  if (!value) return '-'
  const ms = parseTime(value)
  if (!ms) return String(value)
  const date = new Date(ms)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/* ═══════════ 跳转（深链到聊天页） ═══════════ */

function openGroupChat(openId) {
  router.push({ path: '/', query: { group: openId } })
}

function openUserChat(u) {
  router.push({ path: '/', query: { user: u.userOpenId } })
}

/* ═══════════ 复制 ═══════════ */

const copyState = ref('')
let copyTimer = null

function copyText(text) {
  if (!text) return
  const done = () => {
    copyState.value = text
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = setTimeout(() => { copyState.value = '' }, 1200)
  }
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done))
  } else {
    fallbackCopy(text, done)
  }
}

function fallbackCopy(text, done) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  try { document.execCommand('copy'); done() } catch { /* ignore */ }
  document.body.removeChild(ta)
}

/* ═══════════ 权限弹窗 ═══════════ */

const showPermModal = ref(false)
const permTarget = ref('')
const pendingPermRole = ref('')
const pendingPermNodes = ref([])
const pendingIsBlocked = ref(false)
const pendingIsIgnored = ref(false)
const pendingC2CPush = ref(true)
const newPermNode = ref('')
const roles = ['USER', 'ADMIN', 'OWNER']

async function openPermModal(unionOpenId) {
  permTarget.value = unionOpenId
  try {
    const data = await api(`/c2c/${encodeURIComponent(unionOpenId)}/permissions`)
    pendingPermRole.value = data?.role || 'USER'
    pendingPermNodes.value = [...(data?.permissions || [])]
    pendingIsBlocked.value = data?.isBlocked || false
    pendingIsIgnored.value = data?.isIgnored || false
    pendingC2CPush.value = data?.c2cPush !== false
  } catch { pendingPermRole.value = 'USER'; pendingPermNodes.value = []; pendingIsBlocked.value = false; pendingIsIgnored.value = false; pendingC2CPush.value = true }
  newPermNode.value = ''
  showPermModal.value = true
}

async function confirmPerm() {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/role?role=${pendingPermRole.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/blocked?value=${pendingIsBlocked.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/ignored?value=${pendingIsIgnored.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/push?value=${pendingC2CPush.value}`, { method: 'POST' })
  } catch (e) { /* ignore */ }
}

async function addPermNode(perm) {
  if (!permTarget.value || !perm?.trim()) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm.trim())}?enabled=true`, { method: 'POST' })
    pendingPermNodes.value.push(perm.trim())
    newPermNode.value = ''
  } catch {}
}

async function removePermNode(perm) {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm)}?enabled=false`, { method: 'POST' })
    pendingPermNodes.value = pendingPermNodes.value.filter(p => p !== perm)
  } catch {}
}

/* ═══════════ 请求 ═══════════ */

function authHeaders() {
  return { 'Content-Type': 'application/json' }
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) { logout(); throw new Error('WebUI 已关闭') }
  let payload
  try {
    payload = await res.json()
  } catch {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' }).finally(() => {
    router.replace('/login')
  })
}

function applySearch() {
  currentSearch.value = searchText.value.trim()
}

function clearSearch() {
  searchText.value = ''
  currentSearch.value = ''
}

async function loadGroups() {
  loadingGroups.value = true
  groupsError.value = ''
  try {
    groups.value = await api('/groups') || []
  } catch (e) {
    groupsError.value = e.message
  } finally {
    loadingGroups.value = false
  }
}

async function loadUsers() {
  loadingUsers.value = true
  usersError.value = ''
  try {
    users.value = await api('/c2c/users') || []
  } catch (e) {
    usersError.value = e.message
  } finally {
    loadingUsers.value = false
  }
}

async function loadGroupFuncKeys() {
  try {
    groupFuncKeys.value = await api('/groups/functions/keys') || []
  } catch (e) {
    groupFuncKeys.value = []
  }
}

function refreshAll() {
  return Promise.all([loadGroups(), loadUsers(), loadGroupFuncKeys()])
}

onMounted(async () => {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch (e) {
    // ignore
  }
  await Promise.all([loadGroups(), loadUsers(), loadGroupFuncKeys()])
})

onBeforeUnmount(() => {
  broadcastStop.value = true
  if (copyTimer) clearTimeout(copyTimer)
})
</script>

<style scoped>
.broadcast-trigger {
  display: inline-grid; place-items: center; flex: 0 0 30px; width: 30px; height: 30px; padding: 0;
  border: 1px solid var(--color-border-input); border-radius: var(--radius-sm);
  background: var(--color-surface); color: var(--color-text-secondary); cursor: pointer;
  transition: border-color 0.12s, color 0.12s, background 0.12s;
}
.broadcast-trigger svg { width: 17px; height: 17px; }
.broadcast-trigger:hover { border-color: var(--color-accent-border); background: var(--color-accent-soft); color: var(--color-accent-text); }
.broadcast-trigger:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
.broadcast-trigger:disabled { opacity: 0.5; cursor: not-allowed; }
.broadcast-modal { width: min(680px, calc(100vw - 24px)); max-width: none; max-height: min(88vh, 860px); }
.broadcast-body { gap: 14px; min-height: 0; }
.broadcast-field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--color-text); }
.broadcast-field input:not([type="file"]), .broadcast-field select, .broadcast-field textarea {
  width: 100%; min-width: 0; box-sizing: border-box; padding: 9px 10px; border: 1px solid var(--color-border-input);
  border-radius: 8px; background: var(--color-surface); color: var(--color-text); font: inherit;
}
.broadcast-field textarea { resize: vertical; min-height: 110px; }
.broadcast-field input[type="file"] { max-width: 100%; }
.broadcast-picker { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border: 1px solid var(--color-border); border-radius: 8px; font-size: 13px; }
.broadcast-picker-overlay { z-index: 1100; }
.broadcast-picker-dialog { width: min(1040px, calc(100vw - 32px)); height: min(84dvh, 780px); max-width: none; max-height: none; overflow: hidden; }
.broadcast-picker-head > div { min-width: 0; }
.broadcast-picker-head h2 { margin: 0; }
.broadcast-picker-head p { margin: 4px 0 0; color: var(--color-text-muted); font-size: 12px; }
.broadcast-picker-layout { display: grid; grid-template-columns: minmax(0, 1fr) 260px; flex: 1; min-height: 0; }
.broadcast-picker-main { display: flex; flex-direction: column; gap: 12px; min-width: 0; min-height: 0; padding: 18px; }
.broadcast-picker-controls { display: grid; grid-template-columns: minmax(0, 1fr) 210px; gap: 9px; }
.broadcast-picker-controls input, .broadcast-picker-controls select {
  min-width: 0; width: 100%; box-sizing: border-box; padding: 9px 10px;
  border: 1px solid var(--color-border-input); border-radius: 7px;
  background: var(--color-surface); color: var(--color-text); font: inherit; font-size: 12px;
}
.broadcast-picker-actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; font-size: 12px; color: var(--color-text-muted); }
.broadcast-picker-actions > div { display: flex; flex-wrap: wrap; gap: 6px; }
.broadcast-picker-list { flex: 1; min-height: 0; overflow-y: auto; border: 1px solid var(--color-border); border-radius: 8px; }
.broadcast-picker-list > .broadcast-note { padding: 12px; }
.broadcast-picker-row {
  display: grid; grid-template-columns: 16px minmax(0, 1fr) auto; align-items: center; gap: 9px;
  min-height: 42px; padding: 8px 10px; border-bottom: 1px solid var(--color-hairline);
  cursor: pointer; font-size: 12px;
}
.broadcast-picker-row:last-child { border-bottom: 0; }
.broadcast-picker-row:hover { background: var(--color-surface-hover); }
.broadcast-picker-row input { margin: 0; accent-color: var(--color-accent); }
.broadcast-picker-name, .broadcast-picker-meta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.broadcast-picker-meta { max-width: 190px; color: var(--color-text-muted); font-size: 11px; }
.broadcast-picker-selected { display: flex; flex-direction: column; min-width: 0; min-height: 0; border-left: 1px solid var(--color-border); background: var(--color-surface-alt); }
.broadcast-picker-selected-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 14px 12px; border-bottom: 1px solid var(--color-border); font-size: 12px; }
.broadcast-picker-selected-list { flex: 1; min-height: 0; overflow-y: auto; padding: 7px 9px; }
.broadcast-picker-selected-list > .broadcast-note { padding: 8px 4px; }
.broadcast-picker-selected-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 8px; border-radius: 6px; font-size: 12px; }
.broadcast-picker-selected-row:hover { background: var(--color-surface); }
.broadcast-picker-selected-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.broadcast-picker-selected-row button { flex: 0 0 24px; width: 24px; height: 24px; border: 0; border-radius: 5px; background: transparent; color: var(--color-text-muted); cursor: pointer; font-size: 18px; }
.broadcast-picker-selected-row button:hover { background: var(--color-danger-soft); color: var(--color-danger); }
.broadcast-picker-foot { align-items: center; justify-content: space-between; }
.broadcast-picker-foot span { color: var(--color-text-muted); font-size: 11px; }
.broadcast-note { margin: 0; font-size: 12px; line-height: 1.5; color: var(--color-text-muted); }
.broadcast-targets { display: flex; flex-direction: column; gap: 8px; padding: 11px; border: 1px solid var(--color-border); border-radius: 8px; font-size: 12px; }
.broadcast-target-list { display: flex; flex-wrap: wrap; gap: 6px; max-height: 100px; overflow-y: auto; }
.broadcast-target-list span { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 4px 7px; border-radius: 6px; background: var(--color-surface-alt); }
.broadcast-types { display: flex; flex-wrap: wrap; gap: 7px; }
.broadcast-types label { display: inline-flex; align-items: center; gap: 4px; padding: 6px 10px; border: 1px solid var(--color-border); border-radius: 7px; cursor: pointer; font-size: 12px; }
.broadcast-types label.active { border-color: var(--color-accent); color: var(--color-accent); }
.broadcast-ark-item { display: grid; grid-template-columns: 1fr 1fr auto; align-items: end; gap: 8px; }
.broadcast-ark-item .ghost-button { min-height: 36px; }
.broadcast-image-preview { max-width: min(100%, 320px); max-height: 180px; object-fit: contain; align-self: flex-start; border-radius: 7px; }
.broadcast-confirm, .broadcast-progress { margin: 0; padding: 10px; border-radius: 8px; background: var(--color-surface-alt); font-size: 12px; line-height: 1.5; }
.broadcast-failures { margin: 0; padding-left: 22px; max-height: 120px; overflow-y: auto; color: var(--color-danger-strong); font-size: 12px; }
@media (max-width: 600px) {
  .broadcast-modal { max-height: calc(100dvh - 20px); }
  .broadcast-picker-dialog { width: 100vw; height: 100dvh; border-radius: 0; }
  .broadcast-picker-layout { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(0, 1fr) minmax(120px, 25%); }
  .broadcast-picker-main { padding: 12px; gap: 9px; }
  .broadcast-picker-controls { grid-template-columns: minmax(0, 1fr); }
  .broadcast-picker-selected { border-left: 0; border-top: 1px solid var(--color-border); }
  .broadcast-picker-selected-head { padding: 7px 12px; }
  .broadcast-picker-selected-list { display: flex; flex-wrap: wrap; align-content: flex-start; gap: 4px; padding: 6px 10px; }
  .broadcast-picker-selected-row { max-width: 100%; background: var(--color-surface); }
  .broadcast-picker-row { grid-template-columns: 16px minmax(0, 1fr); }
  .broadcast-picker-meta { grid-column: 2; max-width: 100%; }
  .broadcast-picker-foot span { display: none; }
  .broadcast-picker-foot { justify-content: flex-end; }
  .broadcast-ark-item { grid-template-columns: 1fr; }
}
</style>
