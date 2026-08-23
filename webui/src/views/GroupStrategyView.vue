<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer"/>

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="feedback-title">加群策略</h2>
          <div class="feedback-tabs">
            <button :class="{ active: tab === 'strategies' }" @click="switchTab('strategies')">审批策略</button>
            <button :class="{ active: tab === 'requests' }" @click="switchTab('requests')">审批列表</button>
          </div>
        </div>
      </header>

      <section class="content feedback-layout">
        <!-- 策略配置 -->
        <section v-if="tab === 'strategies'" class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>入群审批策略</strong>
            <div class="chat-head-right">
              <span class="status-pill"><span class="dot ok"></span>{{ strategies.length }} 条策略</span>
              <button class="ghost-button" :disabled="refreshing" @click="refreshStrategies">
                {{ refreshing ? '刷新中...' : '刷新' }}
              </button>
              <button class="primary-button" @click="openCreate">新建策略</button>
            </div>
          </div>
          <div class="feedback-content">
            <div v-if="loadingStrategies" class="empty-state">加载中...</div>
            <div v-else-if="strategyError" class="empty-state error">{{ strategyError }}</div>
            <div v-else-if="strategies.length === 0" class="empty-state">暂无策略，点右上角「新建策略」创建</div>
            <div v-else class="feedback-list">
              <article v-for="s in strategies" :key="s.strategyId" class="feedback-card gs-card">
                <div class="feedback-card-head">
                  <span class="gs-card-title">{{ s.remark || '未命名策略' }}</span>
                  <span class="gs-id" :title="s.strategyId">#{{ s.strategyId }}</span>
                  <span v-if="s.enable" class="feedback-tag feedback-tag--replied">已启用</span>
                  <span v-else class="feedback-tag feedback-tag--pending">已停用</span>
                  <span class="feedback-time">更新于 {{ formatTime(s.updatedAt) }}</span>
                </div>
                <div class="gs-card-grid">
                  <div class="gs-field">
                    <div class="gs-field-label">关联群</div>
                    <div class="gs-field-value">{{ s.groupOpenIds.length }} 个{{ groupNames(s.groupOpenIds) }}</div>
                  </div>
                  <div class="gs-field">
                    <div class="gs-field-label">白名单</div>
                    <div class="gs-field-value">{{ s.whitelistUserCount }} 个号码</div>
                  </div>
                  <div class="gs-field">
                    <div class="gs-field-label">过期时间</div>
                    <div class="gs-field-value">{{ formatTime(s.expireAt) }}</div>
                  </div>
                  <div class="gs-field">
                    <div class="gs-field-label">创建时间</div>
                    <div class="gs-field-value">{{ formatTime(s.createdAt) }}</div>
                  </div>
                </div>
                <div class="feedback-card-actions">
                  <button class="ghost-button gs-mini-btn" @click="openEdit(s)">
                    <svg class="gs-act-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                    <span class="gs-act-label">编辑</span>
                  </button>
                  <button class="ghost-button gs-mini-btn" @click="openGroups(s)">
                    <svg class="gs-act-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                    <span class="gs-act-label">关联群</span>
                  </button>
                  <button class="ghost-button gs-mini-btn" @click="openWhitelist(s)">
                    <svg class="gs-act-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                    <span class="gs-act-label">白名单</span>
                  </button>
                  <button class="ghost-button gs-mini-btn" @click="execStrategy(s)">
                    <svg class="gs-act-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                    <span class="gs-act-label">执行扫描</span>
                  </button>
                  <button class="ghost-button danger gs-mini-btn" @click="removeStrategy(s)">
                    <svg class="gs-act-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                    <span class="gs-act-label">删除</span>
                  </button>
                </div>
              </article>
            </div>
          </div>
        </section>

        <!-- 待审批 -->
        <section v-else class="chat-panel feedback-panel">
          <div class="chat-head gs-req-head">
            <strong>入群申请</strong>
            <div class="chat-head-right">
              <select v-model="reqGroupOpenId" class="gs-group-select" @change="loadJoinRequests(true)">
                <option value="">选择群...</option>
                <option v-for="g in groups" :key="g.groupOpenId" :value="g.groupOpenId">{{ g.groupName || g.groupOpenId }}</option>
              </select>
              <button class="ghost-button" :disabled="!reqGroupOpenId || loadingReqs" @click="loadJoinRequests(true)">
                {{ loadingReqs ? '加载中...' : '刷新' }}
              </button>
            </div>
          </div>
          <div class="feedback-content">
            <div v-if="loadingReqs" class="empty-state">加载中...</div>
            <div v-else-if="reqError" class="empty-state error">{{ reqError }}</div>
            <div v-else-if="!reqGroupOpenId" class="empty-state">请先选择群</div>
            <div v-else-if="joinRequests.length === 0" class="empty-state">暂无入群申请</div>
            <div v-else class="feedback-list">
              <article v-for="r in joinRequests" :key="r.joinRequestId" class="feedback-card gs-req-card">
                <div class="feedback-card-head">
                  <span class="gs-req-name">{{ r.username || '未知昵称' }}</span>
                  <span v-if="r.applySource === 'invited'" class="feedback-tag feedback-tag--replied">被邀请</span>
                  <span v-else class="feedback-tag feedback-tag--pending">主动申请</span>
                  <span v-if="r.bot" class="feedback-tag">机器人</span>
                  <span class="feedback-time">{{ formatTime(r.applyAt) }}</span>
                </div>
                <div class="feedback-card-body">
                  <div v-if="r.riskTips" class="gs-req-risk">安全提示: {{ r.riskTips }}</div>
                  <div v-if="r.verifyInfo && r.verifyInfo.method === 'verify_message'" class="gs-req-verify">
                    <div class="feedback-label">验证消息</div>
                    <div class="feedback-text">{{ r.verifyInfo.verifyMessage || '(无)' }}</div>
                  </div>
                  <div v-else-if="r.verifyInfo && r.verifyInfo.method === 'admin_review_qa' && r.verifyInfo.reviewQaList && r.verifyInfo.reviewQaList.length" class="gs-req-verify">
                    <div class="feedback-label">问答验证</div>
                    <div class="gs-req-qa" v-for="(qa, i) in r.verifyInfo.reviewQaList" :key="i">
                      <span class="gs-qa-q">Q: {{ qa.question }}</span>
                      <span class="gs-qa-a">A: {{ qa.answer }}</span>
                    </div>
                  </div>
                </div>
                <div class="feedback-card-actions">
                  <button class="primary-button feedback-action" @click="approveReq(r)">通过</button>
                  <button class="ghost-button feedback-action" @click="openDecline(r)">拒绝</button>
                </div>
              </article>
            </div>
            <div v-if="nextCursor" class="feedback-pagination">
              <button class="ghost-button" :disabled="loadingReqs" @click="loadMore">加载更多</button>
            </div>
          </div>
        </section>
      </section>
    </main>

    <!-- 新建/编辑策略弹窗 -->
    <div v-if="editTarget" class="modal-backdrop" @click.self="closeEdit">
      <div class="modal">
        <div class="modal-head">
          <h2>{{ editTarget.strategyId ? '编辑策略' : '新建策略' }}</h2>
          <button class="icon-button" @click="closeEdit">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <label class="gs-form-row">
            <span class="gs-form-label">备注</span>
            <input v-model="editForm.remark" class="gs-input" type="text" maxlength="255" placeholder="最多 255 汉字，留空则不改动"/>
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="editForm.enable"/> 启用策略
          </label>
          <label class="gs-form-row">
            <span class="gs-form-label">过期时间</span>
            <input v-model="editForm.expireAt" class="gs-input" type="datetime-local"/>
          </label>
          <div class="gs-form-row">
            <span class="gs-form-label">关联群</span>
            <div class="gs-group-list">
              <div v-if="groups.length === 0" class="gs-muted">群列表为空</div>
              <label v-for="g in groups" :key="g.groupOpenId" class="checkbox-label gs-group-opt">
                <input type="checkbox" :value="g.groupOpenId" v-model="editForm.groupOpenIds"/> {{ g.groupName || g.groupOpenId }}
              </label>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeEdit">取消</button>
          <button class="primary-button" :disabled="submitting || (!editTarget.strategyId && editForm.groupOpenIds.length === 0)" @click="saveStrategy">
            {{ submitting ? '提交中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 关联群管理弹窗 -->
    <div v-if="groupTarget" class="modal-backdrop" @click.self="closeGroups">
      <div class="modal">
        <div class="modal-head">
          <h2>管理关联群</h2>
          <button class="icon-button" @click="closeGroups">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <div class="gs-form-row">
            <span class="gs-form-label">关联群</span>
            <div class="gs-group-list">
              <label v-for="g in groups" :key="g.groupOpenId" class="checkbox-label gs-group-opt">
                <input type="checkbox" :value="g.groupOpenId" v-model="groupTarget.selected"/> {{ g.groupName || g.groupOpenId }}
              </label>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeGroups">取消</button>
          <button class="primary-button" :disabled="submitting" @click="saveGroups">{{ submitting ? '提交中...' : '保存' }}</button>
        </div>
      </div>
    </div>

    <!-- 白名单弹窗 -->
    <div v-if="whitelistTarget" class="modal-backdrop" @click.self="closeWhitelist">
      <div class="modal">
        <div class="modal-head">
          <h2>白名单管理</h2>
          <button class="icon-button" @click="closeWhitelist">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <div class="gs-muted" style="margin-bottom:8px">当前策略共配置 {{ whitelistTarget.users.length }} 个QQ号</div>
          <div class="gs-whitelist-list">
            <div v-if="whitelistTarget.users.length === 0" class="gs-muted">暂无白名单号码</div>
            <div v-for="u in whitelistTarget.users" :key="u" class="gs-whitelist-chip">
              <span>{{ u }}</span>
              <button class="gs-chip-del" title="移除" :disabled="submitting" @click="removeOne(u)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
          <label class="gs-form-row">
            <span class="gs-form-label">批量添加</span>
            <textarea v-model="whitelistInput" class="gs-textarea" rows="4" placeholder="每行一个 QQ 号码"/>
          </label>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeWhitelist">关闭</button>
          <button class="primary-button" :disabled="parseNumbers(whitelistInput).length === 0 || submitting" @click="applyWhitelist">
            {{ submitting ? '提交中...' : '添加' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 拒绝申请弹窗 -->
    <div v-if="declineTarget" class="modal-backdrop" @click.self="closeDecline">
      <div class="modal">
        <div class="modal-head">
          <h2>拒绝入群申请</h2>
          <button class="icon-button" @click="closeDecline">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <div class="gs-muted" style="margin-bottom:8px">申请人: {{ declineTarget.username || '未知昵称' }}</div>
          <label class="gs-form-row">
            <span class="gs-form-label">拒绝理由</span>
            <input v-model="declineForm.reason" class="gs-input" type="text" placeholder="可留空"/>
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="declineForm.addBlacklist"/> 同时加入群黑名单
          </label>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeDecline">取消</button>
          <button class="primary-button" :disabled="submitting" @click="doDecline">{{ submitting ? '提交中...' : '确认拒绝' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import {formatTime} from '../lib/time.js'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tab = ref('strategies')

// 群列表（用于群选择与待审批下拉）
const groups = ref([])

// 策略配置
const strategies = ref([])
const loadingStrategies = ref(false)
const refreshing = ref(false)
const strategyError = ref('')

// 待审批
const reqGroupOpenId = ref('')
const joinRequests = ref([])
const nextCursor = ref('')
const loadingReqs = ref(false)
const reqError = ref('')

// 弹窗状态
const editTarget = ref(null)
const editForm = reactive({remark: '', enable: true, expireAt: '', groupOpenIds: []})
const groupTarget = ref(null)
const whitelistTarget = ref(null)
const whitelistInput = ref('')
const declineTarget = ref(null)
const declineForm = reactive({reason: '', addBlacklist: false})
const submitting = ref(false)

function authHeaders() {
  return {'Content-Type': 'application/json'}
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
  let payload
  try {
    payload = await res.json()
  } catch {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 401) {
    logout()
    throw new Error('未授权')
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, {method: 'POST', credentials: 'same-origin'}).finally(() => {
    router.replace('/login')
  })
}

function switchTab(name) {
  tab.value = name
}

async function fetchGroups() {
  try {
    // 审批/策略都要管理权限，只保留机器人是群主/管理员的群
    const all = await api('/groups') || []
    groups.value = all.filter(g => g.memberRole === 'OWNER' || g.memberRole === 'ADMIN')
  } catch (e) {
    // 群列表失败不阻断页面
  }
}

// ============ 策略配置 ============

async function loadStrategies() {
  loadingStrategies.value = true
  strategyError.value = ''
  try {
    strategies.value = await api('/join-approval/strategies') || []
    await refreshWhitelistCounts()
  } catch (e) {
    strategyError.value = e.message
  } finally {
    loadingStrategies.value = false
  }
}

async function refreshStrategies() {
  refreshing.value = true
  strategyError.value = ''
  try {
    strategies.value = await api('/join-approval/strategies/refresh', {method: 'POST'}) || []
    await refreshWhitelistCounts()
  } catch (e) {
    strategyError.value = e.message
  } finally {
    refreshing.value = false
  }
}

// 每次加载/刷新后，用本地白名单镜像覆盖卡片上的号码个数（官方列表的 whitelistUserCount 可能滞后）
async function refreshWhitelistCounts() {
  await Promise.all(strategies.value.map(async s => {
    try {
      const users = await api(`/join-approval/strategies/${s.strategyId}/whitelist`) || []
      s.whitelistUserCount = users.length
    } catch (e) {
      // 单个策略拉取失败不阻断整页
    }
  }))
}

function openCreate() {
  editTarget.value = {strategyId: null}
  editForm.remark = ''
  editForm.enable = true
  editForm.expireAt = ''
  editForm.groupOpenIds = []
}

function openEdit(s) {
  editTarget.value = {strategyId: s.strategyId}
  editForm.remark = s.remark || ''
  editForm.enable = s.enable
  editForm.expireAt = s.expireAt ? toLocalInput(s.expireAt) : ''
  editForm.groupOpenIds = [...(s.groupOpenIds || [])]
}

function closeEdit() {
  editTarget.value = null
}

async function saveStrategy() {
  submitting.value = true
  try {
    if (editTarget.value.strategyId) {
      await api(`/join-approval/strategies/${editTarget.value.strategyId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          enable: editForm.enable,
          expireAt: editForm.expireAt ? toRfc3339(editForm.expireAt) : null,
          remark: editForm.remark || null
        })
      })
    } else {
      await api('/join-approval/strategies/create', {
        method: 'POST',
        body: JSON.stringify({
          groupOpenIds: editForm.groupOpenIds,
          enable: editForm.enable,
          expireAt: editForm.expireAt ? toRfc3339(editForm.expireAt) : null,
          remark: editForm.remark || null
        })
      })
    }
    closeEdit()
    await loadStrategies()
  } catch (e) {
    alert('保存失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

function openGroups(s) {
  groupTarget.value = {strategy: s, selected: [...(s.groupOpenIds || [])]}
}

function closeGroups() {
  groupTarget.value = null
}

async function saveGroups() {
  const strategy = groupTarget.value.strategy
  const existing = new Set(strategy.groupOpenIds || [])
  const selected = new Set(groupTarget.value.selected)
  const toAdd = groupTarget.value.selected.filter(id => !existing.has(id))
  const toDel = (strategy.groupOpenIds || []).filter(id => !selected.has(id))
  if (toAdd.length === 0 && toDel.length === 0) {
    closeGroups()
    return
  }
  submitting.value = true
  try {
    if (toAdd.length) {
      await api(`/join-approval/strategies/${strategy.strategyId}/groups`, {
        method: 'PATCH',
        body: JSON.stringify({op: 'add', groupOpenIds: toAdd})
      })
    }
    if (toDel.length) {
      await api(`/join-approval/strategies/${strategy.strategyId}/groups`, {
        method: 'PATCH',
        body: JSON.stringify({op: 'del', groupOpenIds: toDel})
      })
    }
    closeGroups()
    await loadStrategies()
  } catch (e) {
    alert('保存关联群失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function openWhitelist(s) {
  whitelistTarget.value = {strategyId: s.strategyId, users: []}
  whitelistInput.value = ''
  try {
    whitelistTarget.value.users = await api(`/join-approval/strategies/${s.strategyId}/whitelist`) || []
  } catch (e) {
    alert('加载白名单失败: ' + e.message)
  }
}

function closeWhitelist() {
  whitelistTarget.value = null
}

async function applyWhitelist() {
  const strategyId = whitelistTarget.value.strategyId
  const users = parseNumbers(whitelistInput.value)
  if (users.length === 0) return
  submitting.value = true
  try {
    await api(`/join-approval/strategies/${strategyId}/whitelist`, {
      method: 'POST',
      body: JSON.stringify({op: 'add', users})
    })
    whitelistInput.value = ''
    // 合并去重后即时刷新列表
    const existing = new Set(whitelistTarget.value.users)
    const merged = [...whitelistTarget.value.users]
    for (const u of users) {
      if (!existing.has(u)) merged.push(u)
    }
    whitelistTarget.value.users = merged
    await refreshStrategies()
  } catch (e) {
    alert('添加失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function removeOne(u) {
  if (!confirm(`移除白名单号码 ${u}？`)) return
  const strategyId = whitelistTarget.value.strategyId
  submitting.value = true
  try {
    await api(`/join-approval/strategies/${strategyId}/whitelist`, {
      method: 'POST',
      body: JSON.stringify({op: 'del', users: [u]})
    })
    whitelistTarget.value.users = whitelistTarget.value.users.filter(x => x !== u)
    await refreshStrategies()
  } catch (e) {
    alert('移除失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function execStrategy(s) {
  if (!confirm(`确认对「${s.remark || '未命名策略'}」的全部关联群发起全量扫描？`)) return
  try {
    await api(`/join-approval/strategies/${s.strategyId}/execute`, {method: 'POST'})
    alert('已提交，官方异步执行，约 10 分钟完成')
  } catch (e) {
    alert('执行失败: ' + e.message)
  }
}

async function removeStrategy(s) {
  if (!confirm(`确认删除「${s.remark || '未命名策略'}」？`)) return
  try {
    await api(`/join-approval/strategies/${s.strategyId}`, {method: 'DELETE'})
    await loadStrategies()
  } catch (e) {
    alert('删除失败: ' + e.message)
  }
}

// ============ 待审批 ============

async function loadJoinRequests(reset) {
  if (!reqGroupOpenId.value) return
  if (reset) {
    nextCursor.value = ''
  }
  if (!reset && !nextCursor.value) return
  loadingReqs.value = true
  reqError.value = ''
  try {
    const cursor = reset ? '' : nextCursor.value
    const data = await api(`/groups/${reqGroupOpenId.value}/join-requests?cursor=${encodeURIComponent(cursor)}&limit=20`)
    const list = data.list || []
    if (reset) {
      joinRequests.value = list
    } else {
      joinRequests.value = joinRequests.value.concat(list)
    }
    nextCursor.value = data.nextCursor || ''
  } catch (e) {
    reqError.value = e.message
  } finally {
    loadingReqs.value = false
  }
}

function loadMore() {
  loadJoinRequests(false)
}

async function approveReq(r) {
  if (!confirm(`确认通过「${r.username || '该用户'}」的入群申请？`)) return
  try {
    await api(`/groups/${reqGroupOpenId.value}/join-requests/${r.memberOpenId}/approve`, {
      method: 'POST',
      body: JSON.stringify({joinRequestId: r.joinRequestId})
    })
    await loadJoinRequests(true)
  } catch (e) {
    alert('通过失败: ' + e.message)
  }
}

function openDecline(r) {
  declineTarget.value = r
  declineForm.reason = ''
  declineForm.addBlacklist = false
}

function closeDecline() {
  declineTarget.value = null
}

async function doDecline() {
  const r = declineTarget.value
  submitting.value = true
  try {
    await api(`/groups/${reqGroupOpenId.value}/join-requests/${r.memberOpenId}/decline`, {
      method: 'POST',
      body: JSON.stringify({
        joinRequestId: r.joinRequestId,
        rejectReason: declineForm.reason || null,
        addToMemberBlacklist: declineForm.addBlacklist
      })
    })
    closeDecline()
    await loadJoinRequests(true)
  } catch (e) {
    alert('拒绝失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

// ============ 工具 ============

function groupNames(openIds) {
  if (!openIds || openIds.length === 0) return ''
  const names = openIds.slice(0, 3).map(id => {
    const g = groups.value.find(x => x.groupOpenId === id)
    return g ? g.groupName : shortId(id)
  })
  const rest = openIds.length > 3 ? ` 等 ${openIds.length} 个` : ''
  return ' · ' + names.join(', ') + rest
}

function parseNumbers(text) {
  if (!text) return []
  return text.split('\n').map(s => s.trim()).filter(s => /^\d+$/.test(s))
}

function toRfc3339(localInput) {
  return new Date(localInput).toISOString()
}

function toLocalInput(rfc3339) {
  const date = new Date(rfc3339)
  if (Number.isNaN(date.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function shortId(value) {
  if (!value) return '-'
  return value.length <= 8 ? value : value.substring(0, 8)
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
  await fetchGroups()
  await loadStrategies()
})
</script>
