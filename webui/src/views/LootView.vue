<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="refreshCurrentTab">刷新</button>
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
          <h2 class="feedback-title">抽卡管理</h2>
        </div>
      </header>

      <section class="content userlist-layout">
        <section class="chat-panel userlist-panel loot-panel">
          <div class="chat-head">
            <div class="userlist-source-tabs" role="tablist">
              <button v-for="t in tabs" :key="t.key"
                      class="userlist-source-tab" :class="{ active: tab === t.key }"
                      type="button" role="tab" :aria-selected="tab === t.key"
                      @click="switchTab(t.key)">{{ t.label }}</button>
            </div>
            <span class="status-pill" style="margin-left:auto">
              <span class="dot ok"></span>{{ tabSummary }}
            </span>
          </div>

          <!-- 工具条压在内容区之上做通栏，和 UserListView 的搜索条保持同一套结构。
               放进 .userlist-content 里会被那 18px 内边距顶成一条悬空白块，下边框也会变成一道断线。 -->
          <form v-if="tab === 'items'" class="loot-upload-form" @submit.prevent="createItem">
            <input v-model="createForm.displayName" class="loot-upload-input" placeholder="物品名称" required />
            <input v-model="createForm.description" class="loot-upload-input" placeholder="介绍文案（用于单抽结果卡）" />
            <label class="loot-special-check">
              <input v-model="createForm.special" type="checkbox" />
              特殊卡（不出现在抽卡池，仅可赠送）
            </label>
            <input ref="createFileInput" class="loot-upload-file" type="file" accept="image/*" @change="onCreateFileChange" required />
            <button class="primary-button" type="submit" :disabled="creating">{{ creating ? '上传中...' : '新增物品卡' }}</button>
          </form>

          <div v-else-if="tab === 'leaderboard'" class="userlist-search-bar">
            <input v-model="leaderboardSearchText" class="userlist-search-input" placeholder="搜索用户ID..." @keyup.enter="doLeaderboardSearch" />
            <button class="primary-button" :disabled="loading" @click="doLeaderboardSearch">搜索</button>
            <button v-if="leaderboardSearchText" class="ghost-button" @click="clearLeaderboardSearch">清除</button>
          </div>

          <div v-else-if="tab === 'ownership'" class="userlist-search-bar">
            <input v-model="searchText" class="userlist-search-input" placeholder="搜索用户ID..." @keyup.enter="doSearch" />
            <button class="primary-button" :disabled="loading" @click="doSearch">搜索</button>
            <button v-if="searchText" class="ghost-button" @click="clearSearch">清除</button>
          </div>

          <div class="userlist-content">
            <!-- 提示条只占一行，不再顶掉整个标签页的内容：
                 一次保存失败不该把上传表单和整张卡池一起清空 -->
            <div v-if="error" class="loot-banner danger" role="alert">
              <span>{{ error }}</span>
              <button class="loot-banner-close" type="button" aria-label="关闭" @click="error = ''">×</button>
            </div>
            <div v-if="notice" class="loot-banner ok" role="status">
              <span>{{ notice }}</span>
              <button class="loot-banner-close" type="button" aria-label="关闭" @click="notice = ''">×</button>
            </div>

            <!-- 卡片管理 -->
            <template v-if="tab === 'items'">
              <div v-if="loading" class="empty-state">加载中...</div>
              <div v-else-if="items.length === 0" class="empty-state">暂无物品卡</div>
              <div v-else class="loot-grid">
                <article v-for="it in items" :key="it.itemId" class="loot-card">
                  <img v-if="thumbUrl(it.itemId) && !brokenThumbs.has(it.itemId)"
                       class="loot-thumb" :src="thumbUrl(it.itemId)" :alt="it.displayName"
                       loading="lazy" @error="brokenThumbs.add(it.itemId)" />
                  <div v-else class="loot-thumb loot-thumb-empty">无图</div>
                  <div class="loot-card-body">
                    <input v-model="it.displayName" class="loot-inline-input" aria-label="物品名称" />
                    <textarea v-model="it.description" class="loot-inline-textarea" rows="2" placeholder="介绍文案"></textarea>
                    <div class="loot-card-actions">
                      <button class="ghost-button small" type="button" :disabled="savingId === it.itemId"
                              @click="saveItem(it)">{{ savingId === it.itemId ? '保存中...' : '保存' }}</button>
                      <label class="ghost-button small loot-file-label">
                        换图
                        <input type="file" accept="image/*" style="display:none" @change="e => replaceImage(it.itemId, e)" />
                      </label>
                      <button class="ghost-button small danger" type="button" @click="deleteItem(it)">删除</button>
                    </div>
                    <div class="loot-card-meta">ID: {{ it.itemId }}</div>
                  </div>
                </article>
              </div>
              <div class="userlist-bottom-pager" v-if="itemsTotalPages > 1">
                <button class="userlist-page-btn" :disabled="itemsPage <= 1" @click="gotoItemsPage(itemsPage - 1)">‹</button>
                <span class="userlist-page-indicator">{{ itemsPage }} / {{ itemsTotalPages }}</span>
                <button class="userlist-page-btn" :disabled="itemsPage >= itemsTotalPages" @click="gotoItemsPage(itemsPage + 1)">›</button>
              </div>
            </template>

            <!-- 金粒排行榜 -->
            <template v-else-if="tab === 'leaderboard'">
              <div v-if="loading" class="empty-state">加载中...</div>
              <div v-else-if="leaderboard.length === 0" class="empty-state">暂无数据</div>
              <div v-else class="userlist-list">
                <article v-for="row in leaderboard" :key="row.userId" class="userlist-card loot-leaderboard-row">
                  <span class="loot-rank">#{{ row.rank }}</span>
                  <img class="userlist-avatar" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${row.userId}/100`"
                       referrerpolicy="no-referrer" loading="lazy" alt="" @error="$event.target.style.display='none'" />
                  <div class="userlist-info">
                    <div class="userlist-row1"><span class="userlist-id">{{ row.userId }}</span></div>
                  </div>
                  <div class="loot-coins">{{ row.coins }} 金粒</div>
                  <button class="ghost-button small" type="button" @click="openAdjustCoins(row.userId, row.coins)">调整</button>
                </article>
              </div>
              <div class="userlist-bottom-pager" v-if="leaderboardTotalPages > 1">
                <button class="userlist-page-btn" :disabled="leaderboardPage <= 1" @click="gotoLeaderboardPage(leaderboardPage - 1)">‹</button>
                <span class="userlist-page-indicator">{{ leaderboardPage }} / {{ leaderboardTotalPages }}</span>
                <button class="userlist-page-btn" :disabled="leaderboardPage >= leaderboardTotalPages" @click="gotoLeaderboardPage(leaderboardPage + 1)">›</button>
              </div>
            </template>

            <!-- 用户卡片持有 -->
            <template v-else>
              <div v-if="loading" class="empty-state">加载中...</div>
              <div v-else-if="ownershipUsers.length === 0" class="empty-state">暂无数据</div>
              <div v-else class="userlist-list">
                <article v-for="u in ownershipUsers" :key="u.userId" class="userlist-card loot-clickable-row"
                         @click="openUserDetail(u.userId)">
                  <div class="userlist-info">
                    <div class="userlist-row1"><span class="userlist-id">{{ u.userId }}</span></div>
                    <div class="userlist-row2">
                      <span>{{ u.coins }} 金粒</span>
                      <span>持有 {{ u.cardCount }} 张卡片</span>
                    </div>
                  </div>
                </article>
              </div>
              <div class="userlist-bottom-pager" v-if="ownershipTotalPages > 1">
                <button class="userlist-page-btn" :disabled="ownershipPage <= 1" @click="gotoOwnershipPage(ownershipPage - 1)">‹</button>
                <span class="userlist-page-indicator">{{ ownershipPage }} / {{ ownershipTotalPages }}</span>
                <button class="userlist-page-btn" :disabled="ownershipPage >= ownershipTotalPages" @click="gotoOwnershipPage(ownershipPage + 1)">›</button>
              </div>
            </template>
          </div>
        </section>
      </section>
    </main>

    <!-- 用户详情弹窗 -->
    <div v-if="showUserModal" class="modal-backdrop" @click="showUserModal = false">
      <div class="modal" @click.stop>
        <div class="modal-head">
          <h2>{{ userDetail?.userId }}</h2>
          <button class="icon-button" @click="showUserModal = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <p>金粒余额：{{ userDetail?.coins ?? 0 }}</p>
          <h4 style="margin:10px 0 4px">持有物品卡</h4>
          <div v-for="loot in userDetail?.loots || []" :key="loot.itemId" class="func-row">
            <span class="func-name">
              <span v-if="loot.special" class="badge purple loot-special-tag">特殊</span>
              {{ loot.displayName }}<span v-if="loot.count > 1"> ×{{ loot.count }}</span>（{{ loot.way }}）
            </span>
            <button class="perm-del" @click="revokeLoot(loot)">×</button>
          </div>
          <p v-if="!userDetail?.loots?.length" style="color:var(--color-text-subtle)">暂无持有物品卡</p>

          <h4 style="margin:10px 0 4px">赠送物品</h4>
          <!-- 这里用整份目录，不能只用当前分页的 items：
               卡池超过一页时，第二页往后的物品会整个从下拉里消失，根本送不出去 -->
          <form class="perm-add" @submit.prevent="grantLoot">
            <select v-model="grantForm.itemId">
              <option value="" disabled>{{ catalogLoading ? '目录加载中...' : '选择物品' }}</option>
              <option v-for="it in catalogItems" :key="it.itemId" :value="it.itemId">{{ it.displayName }}</option>
            </select>
            <input v-model="grantForm.way" placeholder="获取途径（默认：管理员赠与）" />
            <label class="checkbox-label">
              <input v-model="grantForm.special" type="checkbox" />
              特殊奖励（不计入总收集进度）
            </label>
            <button class="primary-button" :disabled="!grantForm.itemId">赠送</button>
          </form>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="showUserModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 调整金粒弹窗 -->
    <div v-if="showAdjustModal" class="modal-backdrop" @click="showAdjustModal = false">
      <div class="modal" @click.stop>
        <div class="modal-head">
          <h2>调整金粒 - {{ adjustTarget }}</h2>
          <button class="icon-button" @click="showAdjustModal = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="perm-uid">当前余额：{{ adjustCurrentCoins }}</p>
          <div class="perm-roles">
            <button v-for="op in adjustOps" :key="op.key" type="button"
                    :class="['badge', 'clickable', adjustOp === op.key ? 'green' : 'gray']"
                    @click="adjustOp = op.key">{{ op.label }}</button>
          </div>
          <input v-model.number="adjustAmount" type="number" min="0" placeholder="数量" />
          <p class="loot-modal-hint">{{ adjustPreview }}</p>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="showAdjustModal = false">取消</button>
          <button class="primary-button" :disabled="!adjustValid" @click="submitAdjustCoins">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)
const loading = ref(false)
const error = ref('')
const notice = ref('')

let noticeTimer = null
function flash(message) {
  notice.value = message
  if (noticeTimer) clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 2500)
}

const tabs = [
  { key: 'items', label: '卡片管理' },
  { key: 'leaderboard', label: '金粒排行榜' },
  { key: 'ownership', label: '用户卡片持有' },
]
const tab = ref('items')
const tabSummary = computed(() => {
  if (tab.value === 'items') return `${itemsTotal.value} 张卡片`
  if (tab.value === 'leaderboard') return `${leaderboardTotal.value} 名用户`
  return `${ownershipTotal.value} 名用户`
})

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) { logout(); throw new Error('WebUI 已关闭') }
  let payload
  try { payload = await res.json() } catch { throw new Error(`HTTP ${res.status}`) }
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

async function apiUpload(path, formData, method = 'POST') {
  const res = await fetch(`${API_BASE}${path}`, { method, credentials: 'same-origin', body: formData })
  if (res.status === 503) { logout(); throw new Error('WebUI 已关闭') }
  let payload
  try { payload = await res.json() } catch { throw new Error(`HTTP ${res.status}`) }
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' }).finally(() => router.replace('/login'))
}

function switchTab(key) {
  if (tab.value === key) return
  tab.value = key
  error.value = ''
  notice.value = ''
  refreshCurrentTab()
}

function refreshCurrentTab() {
  if (tab.value === 'items') fetchItems()
  else if (tab.value === 'leaderboard') fetchLeaderboard()
  else fetchOwnership()
}

// ==================== 卡片管理 ====================
const items = ref([])
const itemsTotal = ref(0)
const itemsPage = ref(1)
const itemsPageSize = 20
const itemsTotalPages = computed(() => Math.max(1, Math.ceil(itemsTotal.value / itemsPageSize)))
const imageBaseUrl = ref('')
const createForm = ref({ displayName: '', description: '', special: false })
const createFile = ref(null)
const createFileInput = ref(null)
const creating = ref(false)
const savingId = ref('')
const brokenThumbs = reactive(new Set())

function thumbUrl(itemId) {
  // 目录还没回来时不能返回空串：<img src=""> 会让浏览器把当前页面地址再请求一遍
  if (!imageBaseUrl.value) return ''
  return `${imageBaseUrl.value}/${itemId}`
}

function onCreateFileChange(e) {
  createFile.value = e.target.files?.[0] || null
}

function resetCreateFile() {
  createFile.value = null
  // 只清 ref 不清原生 input，文件名会一直挂在那儿，而且再选同一个文件不会触发 change
  if (createFileInput.value) createFileInput.value.value = ''
}

async function fetchItems() {
  loading.value = true
  error.value = ''
  try {
    const data = await api(`/loot/items?page=${itemsPage.value}&pageSize=${itemsPageSize}`)
    items.value = data.items || []
    itemsTotal.value = data.total || 0
    imageBaseUrl.value = data.imageBaseUrl || ''
    items.value.forEach(it => brokenThumbs.delete(it.itemId))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function gotoItemsPage(p) {
  if (p < 1 || p > itemsTotalPages.value) return
  itemsPage.value = p
  fetchItems()
}

async function createItem() {
  if (!createFile.value) { error.value = '请选择图片'; return }
  creating.value = true
  error.value = ''
  try {
    const fd = new FormData()
    fd.append('displayName', createForm.value.displayName)
    fd.append('description', createForm.value.description || '')
    fd.append('special', createForm.value.special ? 'true' : 'false')
    fd.append('image', createFile.value)
    await apiUpload('/loot/items', fd)
    createForm.value = { displayName: '', description: '', special: false }
    resetCreateFile()
    catalogLoaded.value = false
    await fetchItems()
    flash('物品卡已新增')
  } catch (e) {
    error.value = e.message
  } finally {
    creating.value = false
  }
}

async function saveItem(it) {
  savingId.value = it.itemId
  error.value = ''
  try {
    await api(`/loot/items/${encodeURIComponent(it.itemId)}`, {
      method: 'PUT',
      body: JSON.stringify({ displayName: it.displayName, description: it.description })
    })
    catalogLoaded.value = false
    flash(`「${it.displayName}」已保存`)
  } catch (e) {
    error.value = e.message
  } finally {
    savingId.value = ''
  }
}

async function replaceImage(itemId, e) {
  const input = e.target
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  try {
    const fd = new FormData()
    fd.append('image', file)
    await apiUpload(`/loot/items/${encodeURIComponent(itemId)}/image`, fd)
    brokenThumbs.delete(itemId)
    await fetchItems()
    flash('物品图已更换')
  } catch (err) {
    error.value = err.message
  } finally {
    // 清空原生 input，否则连续换同一张图时 change 不会再触发
    input.value = ''
  }
}

async function deleteItem(it) {
  if (!confirm(`确定删除物品卡「${it.displayName}」吗？删掉之后卡池里就没有这张了。`)) return
  error.value = ''
  try {
    await api(`/loot/items/${encodeURIComponent(it.itemId)}`, { method: 'DELETE' })
    catalogLoaded.value = false
    await fetchItems()
    flash('物品卡已删除')
  } catch (e) {
    error.value = e.message
  }
}

// ==================== 完整目录（赠送下拉专用） ====================
const catalogItems = ref([])
const catalogLoading = ref(false)
const catalogLoaded = ref(false)

async function fetchCatalog() {
  if (catalogLoaded.value) return
  catalogLoading.value = true
  try {
    const pageSize = 100 // 服务端上限
    const collected = []
    for (let page = 1; page <= 50; page++) {
      const data = await api(`/loot/items?page=${page}&pageSize=${pageSize}`)
      const batch = data.items || []
      collected.push(...batch)
      if (batch.length < pageSize || collected.length >= (data.total || 0)) break
    }
    catalogItems.value = collected
    catalogLoaded.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    catalogLoading.value = false
  }
}

// ==================== 金粒排行榜 ====================
const leaderboard = ref([])
const leaderboardTotal = ref(0)
const leaderboardPage = ref(1)
const leaderboardPageSize = 20
const leaderboardTotalPages = computed(() => Math.max(1, Math.ceil(leaderboardTotal.value / leaderboardPageSize)))
const leaderboardSearchText = ref('')
const leaderboardCurrentSearch = ref('')

async function fetchLeaderboard() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: leaderboardPage.value, pageSize: leaderboardPageSize })
    if (leaderboardCurrentSearch.value) params.set('search', leaderboardCurrentSearch.value)
    const data = await api(`/loot/coins/leaderboard?${params}`)
    leaderboard.value = data.items || []
    leaderboardTotal.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function gotoLeaderboardPage(p) {
  if (p < 1 || p > leaderboardTotalPages.value) return
  leaderboardPage.value = p
  fetchLeaderboard()
}

function doLeaderboardSearch() {
  leaderboardCurrentSearch.value = leaderboardSearchText.value.trim()
  leaderboardPage.value = 1
  fetchLeaderboard()
}

function clearLeaderboardSearch() {
  leaderboardSearchText.value = ''
  leaderboardCurrentSearch.value = ''
  leaderboardPage.value = 1
  fetchLeaderboard()
}

const showAdjustModal = ref(false)
const adjustTarget = ref('')
const adjustCurrentCoins = ref(0)
const adjustOps = [
  { key: 'set', label: '设为' },
  { key: 'add', label: '增加' },
  { key: 'remove', label: '扣除' },
]
const adjustOp = ref('add')
const adjustAmount = ref(0)

const adjustValid = computed(() => {
  const amount = Number(adjustAmount.value)
  if (!Number.isFinite(amount) || amount < 0) return false
  // 除了「设为 0」，其余操作数量为 0 都是空请求
  return adjustOp.value === 'set' || amount > 0
})

const adjustPreview = computed(() => {
  const amount = Number(adjustAmount.value)
  if (!Number.isFinite(amount)) return ''
  if (adjustOp.value === 'set') return `执行后余额：${Math.max(0, amount)}`
  if (adjustOp.value === 'add') return `执行后余额：${adjustCurrentCoins.value + amount}`
  const after = adjustCurrentCoins.value - amount
  return after < 0 ? '余额不足，服务端会拒绝这次扣除' : `执行后余额：${after}`
})

function openAdjustCoins(userId, currentCoins) {
  adjustTarget.value = userId
  adjustCurrentCoins.value = currentCoins
  adjustOp.value = 'add'
  adjustAmount.value = 0
  showAdjustModal.value = true
}

async function submitAdjustCoins() {
  error.value = ''
  try {
    await api(`/loot/coins/${encodeURIComponent(adjustTarget.value)}`, {
      method: 'POST',
      body: JSON.stringify({ op: adjustOp.value, amount: adjustAmount.value })
    })
    showAdjustModal.value = false
    await fetchLeaderboard()
    flash('金粒已调整')
  } catch (e) {
    error.value = e.message
  }
}

// ==================== 用户卡片持有 ====================
const ownershipUsers = ref([])
const ownershipTotal = ref(0)
const ownershipPage = ref(1)
const ownershipPageSize = 20
const ownershipTotalPages = computed(() => Math.max(1, Math.ceil(ownershipTotal.value / ownershipPageSize)))
const searchText = ref('')
const currentSearch = ref('')

async function fetchOwnership() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: ownershipPage.value, pageSize: ownershipPageSize })
    if (currentSearch.value) params.set('search', currentSearch.value)
    const data = await api(`/loot/users?${params}`)
    ownershipUsers.value = data.items || []
    ownershipTotal.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function gotoOwnershipPage(p) {
  if (p < 1 || p > ownershipTotalPages.value) return
  ownershipPage.value = p
  fetchOwnership()
}

function doSearch() {
  currentSearch.value = searchText.value.trim()
  ownershipPage.value = 1
  fetchOwnership()
}

function clearSearch() {
  searchText.value = ''
  currentSearch.value = ''
  ownershipPage.value = 1
  fetchOwnership()
}

const showUserModal = ref(false)
const userDetail = ref(null)
const grantForm = ref({ itemId: '', way: '', special: false })

async function openUserDetail(userId) {
  error.value = ''
  try {
    userDetail.value = await api(`/loot/users/${encodeURIComponent(userId)}`)
    grantForm.value = { itemId: '', way: '', special: false }
    showUserModal.value = true
    fetchCatalog()
  } catch (e) {
    error.value = e.message
  }
}

async function grantLoot() {
  if (!grantForm.value.itemId || !userDetail.value) return
  const picked = catalogItems.value.find(i => i.itemId === grantForm.value.itemId)
  error.value = ''
  try {
    await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/grant`, {
      method: 'POST',
      body: JSON.stringify({
        itemId: grantForm.value.itemId,
        displayName: picked?.displayName || '',
        way: grantForm.value.way || '管理员赠与',
        special: grantForm.value.special
      })
    })
    userDetail.value = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}`)
    grantForm.value = { itemId: '', way: '', special: false }
    flash('已赠送')
  } catch (e) {
    error.value = e.message
  }
}

async function revokeLoot(loot) {
  if (!userDetail.value) return
  if (!confirm(`确定收回「${loot.displayName}」吗？`)) return
  error.value = ''
  try {
    await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/loots/${encodeURIComponent(loot.itemId)}`, { method: 'DELETE' })
    userDetail.value = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}`)
    await fetchOwnership()
    flash('已收回')
  } catch (e) {
    error.value = e.message
  }
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
  await fetchItems()
})
</script>
