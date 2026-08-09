<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="loadSettings">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <h2>功能设置</h2>
        </div>
      </header>

      <section class="content function-settings-layout">
        <section class="function-settings-page">
          <div class="function-settings-summary">
            <div class="function-settings-hero">
              <span class="function-settings-value">{{ activeCount }}</span>
              <span class="function-settings-label">当前禁用中</span>
            </div>
            <dl class="function-settings-metrics">
              <div class="function-settings-metric">
                <dt>已配置</dt>
                <dd>{{ configuredCount }}</dd>
              </div>
              <div class="function-settings-metric">
                <dt>推送任务</dt>
                <dd>{{ items.length }}</dd>
              </div>
            </dl>
            <p class="function-settings-note">临时禁用推送任务</p>
          </div>

          <div v-if="loading" class="empty-state">加载中...</div>
          <div v-else-if="error" class="empty-state error">{{ error }}</div>
          <div v-else-if="items.length === 0" class="empty-state">暂无推送任务</div>

          <div v-else class="function-settings-list">
            <article v-for="item in items" :key="item.functionId" class="function-setting-row">
              <div class="function-setting-main">
                <div class="function-setting-title-row">
                  <span class="function-setting-title">{{ item.functionName }}</span>
                  <code class="function-setting-id">{{ item.functionId }}</code>
                  <span class="function-setting-state" :class="stateClass(item)">
                    {{ stateText(item) }}
                  </span>
                </div>
                <div class="function-setting-support">
                  <span :class="{ muted: !item.groupEnable }">群聊</span>
                  <span :class="{ muted: !item.c2cEnable }">私聊</span>
                </div>
              </div>

              <div class="function-setting-controls">
                <label class="function-setting-switch">
                  <input v-model="drafts[item.functionId].enabled" type="checkbox" />
                  <span>启用禁用规则</span>
                </label>

                <label class="function-setting-field">
                  <span>场景</span>
                  <select v-model="drafts[item.functionId].scope" class="function-setting-input">
                    <option v-for="opt in scopeOptions(item)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                  </select>
                </label>

                <label class="function-setting-field">
                  <span>开始</span>
                  <input v-model="drafts[item.functionId].startTime" class="function-setting-input" type="datetime-local" />
                </label>

                <label class="function-setting-field">
                  <span>结束</span>
                  <input v-model="drafts[item.functionId].endTime" class="function-setting-input" type="datetime-local" />
                </label>

                <div class="function-setting-actions">
                  <button class="primary-button function-setting-btn"
                          :disabled="saving[item.functionId]"
                          @click="saveSetting(item)">
                    {{ saving[item.functionId] ? '保存中...' : '保存' }}
                  </button>
                  <button class="ghost-button function-setting-btn"
                          :disabled="saving[item.functionId] || !item.enabled"
                          @click="clearSetting(item)">
                    清除
                  </button>
                </div>
              </div>
            </article>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import {computed, reactive, ref, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()

const sidebarOpen = ref(false)
const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')

const loading = ref(false)
const error = ref('')
const items = ref([])
const drafts = reactive({})
const saving = reactive({})

const activeCount = computed(() => items.value.filter(item => item.activeNow).length)
const configuredCount = computed(() => items.value.filter(item => item.enabled).length)

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

async function loadConfig() {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch {
    // ignore
  }
}

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const data = await api('/function-settings')
    items.value = Array.isArray(data) ? data : []
    for (const item of items.value) {
      drafts[item.functionId] = {
        enabled: !!item.enabled,
        scope: normalizeScopeForItem(item, item.scope),
        startTime: toDatetimeInput(item.startTime),
        endTime: toDatetimeInput(item.endTime)
      }
    }
  } catch (e) {
    error.value = e.message
    items.value = []
  } finally {
    loading.value = false
  }
}

function scopeOptions(item) {
  const options = []
  if (item.groupEnable && item.c2cEnable) options.push({value: 'BOTH', label: '群聊 + 私聊'})
  if (item.groupEnable) options.push({value: 'GROUP', label: '仅群聊'})
  if (item.c2cEnable) options.push({value: 'C2C', label: '仅私聊'})
  return options
}

function normalizeScopeForItem(item, scope) {
  const options = scopeOptions(item).map(opt => opt.value)
  if (options.includes(scope)) return scope
  return options[0] || 'BOTH'
}

async function saveSetting(item) {
  const draft = drafts[item.functionId]
  if (!draft) return
  saving[item.functionId] = true
  error.value = ''
  try {
    const saved = await api(`/function-settings/${encodeURIComponent(item.functionId)}`, {
      method: 'POST',
      body: JSON.stringify({
        functionName: item.functionName,
        scope: draft.scope,
        startTime: fromDatetimeInput(draft.startTime),
        endTime: fromDatetimeInput(draft.endTime),
        enabled: !!draft.enabled
      })
    })
    replaceItem(saved)
  } catch (e) {
    error.value = e.message
  } finally {
    saving[item.functionId] = false
  }
}

async function clearSetting(item) {
  saving[item.functionId] = true
  error.value = ''
  try {
    await api(`/function-settings/${encodeURIComponent(item.functionId)}`, {method: 'DELETE'})
    await loadSettings()
  } catch (e) {
    error.value = e.message
  } finally {
    saving[item.functionId] = false
  }
}

function replaceItem(saved) {
  const idx = items.value.findIndex(item => item.functionId === saved.functionId)
  if (idx >= 0) {
    items.value[idx] = saved
  }
  drafts[saved.functionId] = {
    enabled: !!saved.enabled,
    scope: normalizeScopeForItem(saved, saved.scope),
    startTime: toDatetimeInput(saved.startTime),
    endTime: toDatetimeInput(saved.endTime)
  }
}

function stateText(item) {
  if (item.activeNow) return '禁用中'
  if (item.enabled) return '已配置'
  return '未配置'
}

function stateClass(item) {
  return {
    'is-active': item.activeNow,
    'is-configured': item.enabled && !item.activeNow,
    'is-idle': !item.enabled
  }
}

function toDatetimeInput(value) {
  if (!value) return ''
  return String(value).trim().replace(' ', 'T').slice(0, 16)
}

function fromDatetimeInput(value) {
  if (!value) return null
  return String(value).trim().replace('T', ' ') + ':00'
}

onMounted(async () => {
  await loadConfig()
  await loadSettings()
})
</script>
