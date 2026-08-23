<template>
  <div class="shell">
    <AppSidebar ref="sidebarRef" v-model:open="sidebarOpen" :app-id="profile.appId || ''" :bot-open-id="profile.openId || ''"
                :bot-name="profile.botName || 'AtriBot'">
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
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <h2>机器人设置</h2>
        </div>
      </header>

      <section class="content bot-settings-layout">
        <div class="bot-settings-page">

          <!-- 机器人信息 -->
          <div class="errors-surface bs-card bs-profile-card">
            <div class="bs-profile">
              <img v-if="profile.avatarUrl" class="bs-avatar" :src="profile.avatarUrl" alt="机器人头像"
                   referrerpolicy="no-referrer" @error="profile.avatarUrl = ''"/>
              <div v-else class="bs-avatar bs-avatar-placeholder">
                {{ (profile.botName || 'A').slice(0, 1) }}
              </div>
              <div class="bs-profile-main">
                <div class="bs-profile-name-row">
                  <span class="bs-profile-name">{{ profile.botName || 'AtriBot' }}</span>
                  <span class="bs-badge" :class="isDevEnv ? 'bs-badge--sandbox' : 'bs-badge--prod'">{{ envText }}</span>
                  <span v-if="profile.apiSandbox" class="bs-badge bs-badge--mode">沙箱 API</span>
                  <span class="bs-badge bs-badge--mode">{{ connectionModeText }}</span>
                  <div class="bs-share-actions" @mouseenter="cancelCloseShareQr" @mouseleave="scheduleCloseShareQr">
                    <button class="bs-share-icon" :class="{ active: shareQr === 'group' }"
                            title="添加到群/消息列表分享二维码"
                            @mouseenter="openShareQr('group')" @click="toggleShareQr('group')">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                        <circle cx="9" cy="7" r="4"/>
                        <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                        <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                      </svg>
                    </button>
                    <button class="bs-share-icon" :class="{ active: shareQr === 'channel' }"
                            title="添加到频道分享二维码"
                            @mouseenter="openShareQr('channel')" @click="toggleShareQr('channel')">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="4" y1="9" x2="20" y2="9"/>
                        <line x1="4" y1="15" x2="20" y2="15"/>
                        <line x1="10" y1="3" x2="8" y2="21"/>
                        <line x1="16" y1="3" x2="14" y2="21"/>
                      </svg>
                    </button>
                    <div v-if="shareQr" class="bs-share-popover">
                      <template v-if="!qrText">
                        <div class="bs-share-qr-empty">未配置 qq.bot-uin（机器人 QQ 号），无法生成群分享二维码</div>
                      </template>
                      <template v-else>
                        <div class="bs-qr-title">{{ shareQr === 'group' ? '添加到群 / 消息列表' : '添加到频道' }}</div>
                        <div class="bs-qr-wrap">
                          <img v-if="qrDataUrl" class="bs-qr-img" :src="qrDataUrl" alt="分享二维码"/>
                          <img v-if="profile.avatarUrl" class="bs-qr-logo" :src="profile.avatarUrl"
                               referrerpolicy="no-referrer" alt=""/>
                        </div>
                        <div class="bs-qr-url bs-mono bs-ellipsis" :title="qrText">{{ qrText }}</div>
                        <div class="bs-qr-popover-actions">
                          <button class="ghost-button bs-copy-btn" @click="copyQrUrl">{{ qrCopied ? '已复制' : '复制链接' }}</button>
                          <button class="ghost-button bs-copy-btn" @click="downloadQr">下载二维码</button>
                        </div>
                      </template>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <dl class="bs-fields">
              <div class="bs-field">
                <dt>AppID</dt>
                <dd class="bs-mono">{{ profile.appId || '-' }}</dd>
              </div>
              <div class="bs-field">
                <dt>OpenID</dt>
                <dd class="bs-mono">{{ profile.openId || '-' }}</dd>
              </div>
              <div class="bs-field">
                <dt>UnionID</dt>
                <dd class="bs-mono">{{ profile.unionId || '-' }}</dd>
              </div>
              <div class="bs-field">
                <dt>API 地址</dt>
                <dd class="bs-mono">{{ profile.apiBaseUrl || '-' }}</dd>
              </div>
              <div v-if="profile.shareUrl" class="bs-field">
                <dt>分享链接</dt>
                <dd class="bs-share-row">
                  <span class="bs-mono bs-ellipsis" :title="profile.shareUrl">{{ profile.shareUrl }}</span>
                  <button class="ghost-button bs-copy-btn" @click="copyShare">{{ shareCopied ? '已复制' : '复制' }}</button>
                </dd>
              </div>
            </dl>
          </div>

          <!-- 基础设置 -->
          <div class="errors-surface bs-card">
            <header class="bs-card-head">
              <h3 class="bs-card-title">基础设置</h3>
              <p class="bs-card-desc">机器人后端配置</p>
            </header>

            <div v-if="settingsLoading" class="empty-state">加载中...</div>
            <div v-else-if="settingsError" class="empty-state error">{{ settingsError }}</div>
            <template v-else>
              <div v-for="item in settingItems" :key="item.key" class="bs-setting-row">
                <div class="bs-setting-info">
                  <span class="bs-setting-label">
                    {{ item.label }}
                    <span v-if="item.restartRequired" class="bs-tag-restart">重启后生效</span>
                  </span>
                  <span class="bs-setting-desc">{{ item.description }}</span>
                </div>
                <div class="bs-setting-control">
                  <button v-if="item.type === 'boolean'" class="nt-switch" :class="{ on: !!drafts[item.key] }"
                          role="switch" :aria-checked="!!drafts[item.key]" :title="item.label"
                          @click="drafts[item.key] = !drafts[item.key]"><span class="nt-switch-knob"/></button>
                  <input v-else v-model="drafts[item.key]" class="bs-input" :type="item.type === 'int' ? 'number' : 'text'"
                         :placeholder="item.label"/>
                </div>
              </div>

              <footer class="bs-settings-footer">
                <p v-if="saveMessage" class="bs-message ok">{{ saveMessage }}</p>
                <p v-if="saveError" class="bs-message err">{{ saveError }}</p>
                <button class="primary-button bs-save-btn" :disabled="!dirty || savingSettings" @click="saveSettings">
                  {{ savingSettings ? '保存中...' : (dirty ? '保存修改' : '无修改') }}
                </button>
              </footer>
            </template>
          </div>

          <!-- 面板设置 -->
          <div class="errors-surface bs-card">
            <header class="bs-card-head">
              <h3 class="bs-card-title">面板设置</h3>
              <p class="bs-card-desc">WebUI 面板的个性化选项</p>
            </header>

            <div class="bs-setting-row">
              <div class="bs-setting-info">
                <span class="bs-setting-label">恢复默认布局</span>
                <span class="bs-setting-desc">控制面板的页面布局将在下次进入时恢复默认</span>
              </div>
              <div class="bs-setting-control">
                <span v-if="panelResetDone" class="bs-reset-done">已恢复</span>
                <button class="ghost-button bs-reset-btn" @click="resetPanel">重置</button>
              </div>
            </div>
          </div>

          <!-- 指令管理 -->
          <div class="errors-surface bs-card bs-command-card">
            <header class="bs-card-head">
              <h3 class="bs-card-title">指令管理</h3>
              <p class="bs-card-desc">查看后端注册指令，并设置群聊中的临时停用规则</p>
            </header>
            <CommandSettingsPanel ref="commandPanel" :api="api"/>
          </div>

          <!-- 功能配置 -->
          <div class="errors-surface bs-card bs-function-card">
            <header class="bs-card-head">
              <h3 class="bs-card-title">功能配置</h3>
              <p class="bs-card-desc">设置推送任务的临时停用范围和生效时间</p>
            </header>
            <FunctionSettingsPanel ref="functionPanel" :api="api"/>
          </div>

        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import {computed, reactive, ref, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import FunctionSettingsPanel from '../components/FunctionSettingsPanel.vue'
import CommandSettingsPanel from '../components/CommandSettingsPanel.vue'
import {resetPanelLayout} from '../lib/panelLayout.js'
import QRCode from 'qrcode'

const router = useRouter()

const sidebarOpen = ref(false)
const sidebarRef = ref(null)
const panelResetDone = ref(false)
const profile = reactive({botName: '', appId: '', openId: '', unionId: '', avatarUrl: '', apiBaseUrl: '',
  env: 'production', apiSandbox: false, connectionMode: '', shareUrl: '', botUin: ''})

const settingItems = ref([])
const drafts = reactive({})
const settingsLoading = ref(false)
const settingsError = ref('')
const savingSettings = ref(false)
const saveMessage = ref('')
const saveError = ref('')
const functionPanel = ref(null)
const commandPanel = ref(null)

const loading = computed(() => settingsLoading.value)
const connectionModeText = computed(() => profile.connectionMode === 'webhook' ? 'Webhook' : 'WebSocket')
// 环境徽章以 config 的 env 字段为准，QQ 沙箱 API 另用独立小徽章展示
const isDevEnv = computed(() => !!profile.env && profile.env !== 'production')
const envText = computed(() => {
  if (!profile.env || profile.env === 'production') return '正式环境'
  if (['dev', 'development', 'local'].includes(profile.env)) return '开发环境'
  return `${profile.env} 环境`
})
const dirty = computed(() => settingItems.value.some(item => !isEqual(drafts[item.key], item.value)))

function isEqual(a, b) {
  if (typeof a === 'boolean' || typeof b === 'boolean') return !!a === !!b
  return String(a ?? '') === String(b ?? '')
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {'Content-Type': 'application/json'},
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

async function loadProfile() {
  try {
    Object.assign(profile, await api('/bot/profile'))
  } catch {
    // 资料拉取失败不阻断设置区
  }
}

async function loadSettings() {
  settingsLoading.value = true
  settingsError.value = ''
  saveMessage.value = ''
  saveError.value = ''
  try {
    settingItems.value = await api('/bot/settings') || []
    for (const item of settingItems.value) {
      drafts[item.key] = item.value
    }
  } catch (e) {
    settingsError.value = e.message
  } finally {
    settingsLoading.value = false
  }
}

function applyItems(items) {
  settingItems.value = items || []
  for (const item of settingItems.value) {
    drafts[item.key] = item.value
  }
}

async function saveSettings() {
  const updates = {}
  for (const item of settingItems.value) {
    if (!isEqual(drafts[item.key], item.value)) {
      updates[item.key] = item.type === 'int' ? (Number(drafts[item.key]) || 0) : drafts[item.key]
    }
  }
  if (Object.keys(updates).length === 0) return
  savingSettings.value = true
  saveMessage.value = ''
  saveError.value = ''
  try {
    const items = await api('/bot/settings', {method: 'PUT', body: JSON.stringify({updates})})
    applyItems(items)
    const restartItems = settingItems.value.filter(item => item.restartRequired && item.key in updates)
    saveMessage.value = '已保存' + (restartItems.length > 0 ? `，其中 ${restartItems.map(i => i.label).join('、')} 需重启生效` : '')
  } catch (e) {
    saveError.value = e.message
  } finally {
    savingSettings.value = false
  }
}

function refreshAll() {
  loadProfile()
  loadSettings()
  functionPanel.value?.load()
  commandPanel.value?.load()
}

function resetPanel() {
  resetPanelLayout()
  sidebarRef.value?.resetCollapsed()
  panelResetDone.value = true
  setTimeout(() => {
    panelResetDone.value = false
  }, 1500)
}

const shareCopied = ref(false)
const qrCopied = ref(false)

async function copyText(value) {
  if (!value) return false
  let ok = false
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value)
      ok = true
    } catch {
      ok = false
    }
  }
  if (!ok) {
    // 局域网 HTTP 下 clipboard API 不可用（非安全上下文），回退 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      ok = document.execCommand('copy')
    } catch {
      ok = false
    }
    document.body.removeChild(textarea)
  }
  return ok
}

function flashCopied(state) {
  state.value = true
  setTimeout(() => {
    state.value = false
  }, 1500)
}

async function copyShare() {
  if (await copyText(profile.shareUrl)) flashCopied(shareCopied)
  else alert('复制失败，请手动选择文本')
}

// ---- 分享二维码（参考 QQ 客户端的两个分享入口） ----

const shareQr = ref(null) // null | 'group' | 'channel'
const qrDataUrl = ref('')

const qrUrls = computed(() => {
  if (!profile.appId) return {}
  return {
    channel: `https://qun.qq.com/qunpro/robot/share?robot_appid=${profile.appId}`,
    group: profile.botUin
      ? `https://qun.qq.com/qunpro/robot/qunshare?robot_appid=${profile.appId}&robot_uin=${profile.botUin}`
      : null
  }
})
const qrText = computed(() => qrUrls.value[shareQr.value] || '')

async function openShareQr(kind) {
  if (shareQr.value === kind) return
  shareQr.value = kind
  qrCopied.value = false
  await renderQr()
}

function closeShareQr() {
  clearTimeout(shareQrCloseTimer)
  shareQr.value = null
}

// 关闭走 200ms 延迟：图标与气泡间有空隙，直穿会瞬时触发 mouseleave
let shareQrCloseTimer = null

function scheduleCloseShareQr() {
  clearTimeout(shareQrCloseTimer)
  shareQrCloseTimer = setTimeout(() => {
    shareQr.value = null
  }, 200)
}

function cancelCloseShareQr() {
  clearTimeout(shareQrCloseTimer)
}

async function toggleShareQr(kind) {
  shareQr.value = shareQr.value === kind ? null : kind
  qrCopied.value = false
  await renderQr()
}

async function renderQr() {
  const text = qrText.value
  if (!text) {
    qrDataUrl.value = ''
    return
  }
  try {
    // H 级纠错给中央头像 logo 留冗余，2 倍尺寸保证清晰
    qrDataUrl.value = await QRCode.toDataURL(text, {width: 220, margin: 1, errorCorrectionLevel: 'H'})
  } catch {
    qrDataUrl.value = ''
  }
}

async function copyQrUrl() {
  if (await copyText(qrText.value)) flashCopied(qrCopied)
  else alert('复制失败，请手动选择文本')
}

async function downloadQr() {
  const text = qrText.value
  if (!text) return
  const title = shareQr.value === 'group' ? '添加到群 / 消息列表' : '添加到频道'
  try {
    // 下载用 440px 重新生成，比悬浮展示的 220px 更清晰
    const qrImg = await loadImage(
      await QRCode.toDataURL(text, {width: 440, margin: 1, errorCorrectionLevel: 'H'}), false)
    // 头像带 crossOrigin 请求，图床不允许匿名跨域时降级为纯二维码
    let avatar = null
    if (profile.avatarUrl) {
      try {
        avatar = await loadImage(profile.avatarUrl, true)
      } catch {
        avatar = null
      }
    }

    const PAD = 32
    const QR_SIZE = 440
    const TITLE_SIZE = 30
    const width = QR_SIZE + PAD * 2
    const qrY = PAD + TITLE_SIZE + 24
    const height = qrY + QR_SIZE + PAD
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, width, height)
    ctx.fillStyle = '#0d0f13'
    ctx.font = `600 ${TITLE_SIZE}px -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif`
    ctx.textAlign = 'center'
    ctx.fillText(title, width / 2, PAD + TITLE_SIZE - 4)
    ctx.drawImage(qrImg, PAD, qrY, QR_SIZE, QR_SIZE)
    if (avatar) {
      const cx = width / 2
      const cy = qrY + QR_SIZE / 2
      ctx.beginPath()
      ctx.arc(cx, cy, 64, 0, Math.PI * 2)
      ctx.fillStyle = '#ffffff'
      ctx.fill()
      ctx.lineWidth = 3
      ctx.strokeStyle = '#d5d9e0'
      ctx.stroke()
      ctx.save()
      ctx.beginPath()
      ctx.arc(cx, cy, 54, 0, Math.PI * 2)
      ctx.clip()
      ctx.drawImage(avatar, cx - 54, cy - 54, 108, 108)
      ctx.restore()
    }

    const link = document.createElement('a')
    link.download = `${(profile.botName || 'AtriBot')}-${title.replace(/\s+/g, '')}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
  } catch {
    alert('生成二维码图片失败')
  }
}

function loadImage(src, withCors) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    if (withCors) img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}

onMounted(async () => {
  await loadProfile()
  await loadSettings()
  functionPanel.value?.load()
  commandPanel.value?.load()
})
</script>
