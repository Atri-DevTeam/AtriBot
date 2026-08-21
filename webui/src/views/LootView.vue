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
              特殊类型卡
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
              <div v-else class="loot-owner-grid">
                <article v-for="u in ownershipUsers" :key="u.userId" class="loot-owner-card"
                         role="button" tabindex="0" :aria-label="`查看用户 ${u.userId} 的卡片库存`"
                         @click="openUserDetail(u.userId)"
                         @keydown.enter.prevent="openUserDetail(u.userId)"
                         @keydown.space.prevent="openUserDetail(u.userId)">
                  <div class="loot-owner-card-head">
                    <img class="loot-owner-avatar" :src="userAvatarUrl(u.userId)" referrerpolicy="no-referrer"
                         loading="lazy" alt="" @error="$event.target.style.display = 'none'" />
                    <div class="loot-owner-identity">
                      <span class="loot-owner-label">用户库存</span>
                      <strong class="loot-owner-id" :title="u.userId">{{ u.userId }}</strong>
                    </div>
                  </div>
                  <div class="loot-owner-stats">
                    <div class="loot-owner-stat">
                      <span>金粒余额</span>
                      <strong>{{ u.coins }}</strong>
                    </div>
                    <div class="loot-owner-stat">
                      <span>持有卡片</span>
                      <strong>{{ u.cardCount }} <small>张</small></strong>
                    </div>
                  </div>
                  <button class="ghost-button small loot-owner-open" type="button" @click.stop="openUserDetail(u.userId)">查看库存</button>
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
      <div class="modal loot-owner-modal" @click.stop>
        <div class="modal-head">
          <div class="loot-owner-modal-title">
            <img class="loot-owner-avatar" :src="userAvatarUrl(userDetail?.userId)" referrerpolicy="no-referrer" alt=""
                 @error="$event.target.style.display = 'none'" />
            <div>
              <span>用户卡片库存</span>
              <h2>{{ userDetail?.userId }}</h2>
            </div>
          </div>
          <button class="icon-button" aria-label="关闭" @click="showUserModal = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body loot-owner-modal-body">
          <section class="loot-owner-overview" aria-label="库存概览">
            <div>
              <span>金粒余额</span>
              <strong>{{ userDetail?.coins ?? 0 }}</strong>
            </div>
            <div>
              <span>持有种类</span>
              <strong>{{ userDetail?.loots?.length ?? 0 }} <small>种</small></strong>
            </div>
            <div>
              <span>持有总数</span>
              <strong>{{ userLootCount }} <small>张</small></strong>
            </div>
          </section>

          <section class="loot-owner-section">
            <div class="loot-owner-section-head">
              <div>
                <span class="loot-owner-section-kicker">当前库存</span>
                <h3>持有物品卡</h3>
              </div>
              <span class="badge gray">{{ userLootCount }} 张</span>
            </div>
            <div v-if="userDetail?.loots?.length" class="loot-owner-selection-bar">
              <span>已选 {{ selectedLootIds.size }} 种</span>
              <button class="ghost-button small" type="button" :disabled="lootBatchBusy" @click="toggleAllLoots">
                {{ allLootsSelected ? '取消全选' : '全选' }}
              </button>
              <button class="ghost-button small danger" type="button" :disabled="lootBatchBusy || selectedLootIds.size === 0"
                      @click="revokeSelectedLoots">
                {{ lootBatchBusy ? '处理中...' : '批量收回' }}
              </button>
              <button class="ghost-button small loot-owner-special-action" type="button" :disabled="lootBatchBusy || selectedLootIds.size === 0"
                      @click="setSelectedLootsSpecial">
                {{ lootBatchBusy ? '处理中...' : selectedLootsAllSpecial ? '取消特殊' : '设为特殊' }}
              </button>
            </div>
            <div v-if="userDetail?.loots?.length" class="loot-owner-inventory">
              <article v-for="loot in userDetail.loots" :key="loot.itemId" class="loot-owner-loot"
                       :class="{ selected: selectedLootIds.has(loot.itemId) }" @click="toggleLootSelection(loot.itemId)">
                <label class="loot-owner-check" @click.stop>
                  <input type="checkbox" :checked="selectedLootIds.has(loot.itemId)" @change="toggleLootSelection(loot.itemId)" />
                </label>
                <img v-if="lootThumbUrl(loot.itemId) && !brokenDetailThumbs.has(loot.itemId)"
                     class="loot-owner-loot-thumb" :src="lootThumbUrl(loot.itemId)" :alt="loot.displayName"
                     loading="lazy" @error="brokenDetailThumbs.add(loot.itemId)" />
                <div v-else class="loot-owner-loot-thumb loot-thumb-empty">无图</div>
                <div class="loot-owner-loot-copy">
                  <div class="loot-owner-loot-name">
                    <span v-if="loot.special" class="loot-owner-special-mark" role="img" aria-label="特殊卡" title="特殊卡"></span>
                    <strong :title="loot.displayName">{{ loot.displayName }}</strong>
                    <span v-if="loot.count > 1" class="loot-owner-loot-count">×{{ loot.count }}</span>
                  </div>
                  <span class="loot-owner-loot-way" :title="loot.way">{{ loot.way || '未记录' }}</span>
                  <time class="loot-owner-loot-time" :datetime="lootReceiveDateTime(loot)"
                        :title="formatLootReceiveTime(loot)">{{ formatLootReceiveTime(loot) }}</time>
                </div>
                <div class="loot-owner-loot-actions">
                  <button class="ghost-button small danger" type="button" :disabled="lootBatchBusy"
                          @click.stop="revokeLoot(loot)">扣除单张</button>
                  <button class="ghost-button small danger" type="button" :disabled="lootBatchBusy"
                          @click.stop="revokeAllLoot(loot)">扣除全部</button>
                </div>
              </article>
            </div>
            <p v-else class="loot-owner-empty">暂无持有物品卡</p>
          </section>

          <section class="loot-owner-section loot-owner-grant-section">
            <div class="loot-owner-section-head">
              <div>
                <span class="loot-owner-section-kicker">管理员操作</span>
                <h3>批量赠送物品</h3>
              </div>
              <span class="badge gray">已选 {{ selectedGrantItemIds.size }} 张</span>
            </div>
            <div class="loot-owner-grant-form">
              <label>
                <span>获取途径</span>
                <input v-model="grantForm.way" placeholder="默认：管理员赠与" />
              </label>
              <button class="primary-button" type="button" :disabled="grantBatchBusy || selectedGrantItemIds.size === 0"
                      @click="grantSelectedLoots">
                {{ grantBatchBusy ? '赠送中...' : `赠送已选 ${selectedGrantItemIds.size} 张` }}
              </button>
              <label class="checkbox-label loot-owner-special-check">
                <input v-model="grantForm.special" type="checkbox" />
                作为特殊奖励赠送
              </label>
            </div>
            <div class="loot-owner-catalog-head">
              <input v-model="catalogSearch" class="loot-owner-catalog-search" placeholder="筛选物品卡..." />
              <button class="ghost-button small" type="button" :disabled="catalogLoading || !filteredCatalogItems.length"
                      @click="toggleAllGrantItems">
                {{ allGrantItemsSelected ? '取消全选结果' : '全选结果' }}
              </button>
            </div>
            <p v-if="catalogLoading" class="loot-owner-empty">目录加载中...</p>
            <p v-else-if="filteredCatalogItems.length === 0" class="loot-owner-empty">没有匹配的物品卡</p>
            <div v-else class="loot-owner-catalog-grid">
              <article v-for="it in filteredCatalogItems" :key="it.itemId" class="loot-owner-catalog-card"
                       :class="{ selected: selectedGrantItemIds.has(it.itemId) }" @click="toggleGrantSelection(it.itemId)">
                <label class="loot-owner-check" @click.stop>
                  <input type="checkbox" :checked="selectedGrantItemIds.has(it.itemId)" @change="toggleGrantSelection(it.itemId)" />
                </label>
                <img v-if="catalogThumbUrl(it.itemId) && !brokenCatalogThumbs.has(it.itemId)"
                     class="loot-owner-catalog-thumb" :src="catalogThumbUrl(it.itemId)" :alt="it.displayName"
                     loading="lazy" @error="brokenCatalogThumbs.add(it.itemId)" />
                <div v-else class="loot-owner-catalog-thumb loot-thumb-empty">无图</div>
                <strong :title="it.displayName">{{ it.displayName }}</strong>
              </article>
            </div>
          </section>
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
      if (!imageBaseUrl.value && data.imageBaseUrl) imageBaseUrl.value = data.imageBaseUrl
      const batch = (data.items || []).map(normalizeCatalogItem).filter(item => item.itemId)
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
const userLootCount = computed(() => (userDetail.value?.loots || []).reduce((total, loot) => total + (Number(loot.count) || 1), 0))
const searchText = ref('')
const currentSearch = ref('')

function userAvatarUrl(userId) {
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${userId || ''}/100`
}

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
const grantForm = ref({ way: '', special: false })
const selectedLootIds = ref(new Set())
const selectedGrantItemIds = ref(new Set())
const lootBatchBusy = ref(false)
const grantBatchBusy = ref(false)
const catalogSearch = ref('')
const brokenDetailThumbs = reactive(new Set())
const brokenCatalogThumbs = reactive(new Set())

const allLootsSelected = computed(() => {
  const loots = userDetail.value?.loots || []
  return loots.length > 0 && loots.every(loot => selectedLootIds.value.has(loot.itemId))
})
const selectedLootsAllSpecial = computed(() => {
  const selected = (userDetail.value?.loots || [])
    .filter(loot => selectedLootIds.value.has(loot.itemId))
  return selected.length > 0 && selected.every(loot => loot.special)
})
const filteredCatalogItems = computed(() => {
  const keyword = catalogSearch.value.trim().toLowerCase()
  if (!keyword) return catalogItems.value
  return catalogItems.value.filter(item => `${item.displayName} ${item.itemId}`.toLowerCase().includes(keyword))
})
const allGrantItemsSelected = computed(() => {
  const visible = filteredCatalogItems.value
  return visible.length > 0 && visible.every(item => selectedGrantItemIds.value.has(item.itemId))
})

function lootReceiveDate(loot) {
  const timestamp = loot?.receiveTimestamp ?? loot?.receive_timestamp
  if (!timestamp) return null
  const numericTimestamp = Number(timestamp)
  const milliseconds = Number.isFinite(numericTimestamp) && numericTimestamp < 10_000_000_000
    ? numericTimestamp * 1000
    : numericTimestamp
  const date = new Date(milliseconds)
  return Number.isNaN(date.getTime()) ? null : date
}

function lootReceiveDateTime(loot) {
  return lootReceiveDate(loot)?.toISOString()
}

function formatLootReceiveTime(loot) {
  const date = lootReceiveDate(loot)
  if (!date) return '时间未记录'
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function normalizeCatalogItem(item) {
  return {
    ...item,
    itemId: item?.itemId || item?.item_id || '',
    displayName: item?.displayName || item?.display_name || '未命名物品卡'
  }
}

function lootThumbUrl(itemId) {
  const base = userDetail.value?.imageBaseUrl || ''
  return base ? `${base}/${itemId}` : ''
}

function catalogThumbUrl(itemId) {
  const base = userDetail.value?.imageBaseUrl || imageBaseUrl.value
  return base ? `${base}/${itemId}` : ''
}

function resetUserSelections() {
  selectedLootIds.value = new Set()
  selectedGrantItemIds.value = new Set()
  catalogSearch.value = ''
  brokenDetailThumbs.clear()
  brokenCatalogThumbs.clear()
}

async function reloadUserDetail() {
  if (!userDetail.value) return
  userDetail.value = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}`)
}

async function openUserDetail(userId) {
  error.value = ''
  try {
    userDetail.value = await api(`/loot/users/${encodeURIComponent(userId)}`)
    grantForm.value = { way: '', special: false }
    resetUserSelections()
    showUserModal.value = true
    fetchCatalog()
  } catch (e) {
    error.value = e.message
  }
}

function toggleLootSelection(itemId) {
  const next = new Set(selectedLootIds.value)
  if (next.has(itemId)) next.delete(itemId)
  else next.add(itemId)
  selectedLootIds.value = next
}

function toggleAllLoots() {
  selectedLootIds.value = allLootsSelected.value
    ? new Set()
    : new Set((userDetail.value?.loots || []).map(loot => loot.itemId))
}

function toggleGrantSelection(itemId) {
  const next = new Set(selectedGrantItemIds.value)
  if (next.has(itemId)) next.delete(itemId)
  else next.add(itemId)
  selectedGrantItemIds.value = next
}

function toggleAllGrantItems() {
  const next = new Set(selectedGrantItemIds.value)
  for (const item of filteredCatalogItems.value) {
    if (allGrantItemsSelected.value) next.delete(item.itemId)
    else next.add(item.itemId)
  }
  selectedGrantItemIds.value = next
}

async function grantSelectedLoots() {
  if (!userDetail.value || selectedGrantItemIds.value.size === 0) return
  const selected = catalogItems.value.filter(item => selectedGrantItemIds.value.has(item.itemId))
  if (!selected.length) return
  grantBatchBusy.value = true
  error.value = ''
  try {
    const result = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/grant-batch`, {
      method: 'POST',
      body: JSON.stringify({
        items: selected.map(item => ({ itemId: item.itemId, displayName: item.displayName })),
        way: grantForm.value.way || '管理员赠与',
        special: grantForm.value.special
      })
    })
    await reloadUserDetail()
    await fetchOwnership()
    selectedGrantItemIds.value = new Set()
    flash(`已赠送 ${result.success}/${result.total} 张`)
  } catch (e) {
    error.value = e.message
  } finally {
    grantBatchBusy.value = false
  }
}

async function revokeSelectedLoots() {
  if (!userDetail.value || selectedLootIds.value.size === 0) return
  const itemIds = [...selectedLootIds.value]
  if (!confirm(`确定收回选中的 ${itemIds.length} 种物品卡吗？每种只收回 1 张。`)) return
  lootBatchBusy.value = true
  error.value = ''
  try {
    const result = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/loots/revoke-batch`, {
      method: 'POST', body: JSON.stringify({ itemIds })
    })
    await reloadUserDetail()
    await fetchOwnership()
    selectedLootIds.value = new Set()
    flash(`已收回 ${result.success}/${result.total} 张`)
  } catch (e) {
    error.value = e.message
  } finally {
    lootBatchBusy.value = false
  }
}

async function setSelectedLootsSpecial() {
  if (!userDetail.value || selectedLootIds.value.size === 0) return
  const itemIds = [...selectedLootIds.value]
  const special = !selectedLootsAllSpecial.value
  const action = special ? '设为特殊' : '取消特殊'
  if (!confirm(`确定将选中的 ${itemIds.length} 种物品卡${action}吗？此操作不会改变数量。`)) return
  lootBatchBusy.value = true
  error.value = ''
  try {
    const result = await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/loots/set-special`, {
      method: 'POST', body: JSON.stringify({ itemIds, special })
    })
    await reloadUserDetail()
    await fetchOwnership()
    selectedLootIds.value = new Set()
    flash(`已${action} ${result.success}/${result.total} 种`)
  } catch (e) {
    error.value = e.message
  } finally {
    lootBatchBusy.value = false
  }
}

async function revokeLoot(loot) {
  if (!userDetail.value) return
  if (!confirm(`确定扣除「${loot.displayName}」1 张吗？`)) return
  lootBatchBusy.value = true
  error.value = ''
  try {
    await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/loots/${encodeURIComponent(loot.itemId)}`, { method: 'DELETE' })
    await reloadUserDetail()
    await fetchOwnership()
    const next = new Set(selectedLootIds.value)
    next.delete(loot.itemId)
    selectedLootIds.value = next
    flash('已扣除 1 张')
  } catch (e) {
    error.value = e.message
  } finally {
    lootBatchBusy.value = false
  }
}

async function revokeAllLoot(loot) {
  if (!userDetail.value) return
  if (!confirm(`确定扣除「${loot.displayName}」的全部 ${loot.count} 张吗？此操作会移除该物品的全部副本。`)) return
  lootBatchBusy.value = true
  error.value = ''
  try {
    await api(`/loot/users/${encodeURIComponent(userDetail.value.userId)}/loots/${encodeURIComponent(loot.itemId)}/revoke-all`, {
      method: 'POST'
    })
    await reloadUserDetail()
    await fetchOwnership()
    const next = new Set(selectedLootIds.value)
    next.delete(loot.itemId)
    selectedLootIds.value = next
    flash(`已扣除全部 ${loot.count} 张`)
  } catch (e) {
    error.value = e.message
  } finally {
    lootBatchBusy.value = false
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
