<script setup>
import {computed, onMounted, onBeforeUnmount, ref} from 'vue'
import AppShell from '../../shared/components/AppShell.vue'
import {createWebuiApi, logoutWebui} from '../../shared/lib/webuiApi.js'
import {API_BASE} from '../../router.js'

const sidebarOpen = ref(false)
const catalogOpen = ref(false)
const endpoint = '/connect/user/background'
const catalogSearch = ref('')
const endpointVisible = computed(() => `用户数据 用户背景图信息 GET ${endpoint}`.toLowerCase().includes(catalogSearch.value.trim().toLowerCase()))
const identityKey = ref('b_id')
const identity = ref('')
const identityTypes = [
  {key: 'b_id', label: '机器人 OpenID'},
  {key: 'c_id', label: 'QQ 互联 OpenID'}, {
    key: 'u_id',
    label: '统一账号 UUID'
  }]
const ticket = ref('')
const generatedTicket = ref('')
const expiresAt = ref(0)
const enabled = ref(true)
const refreshing = ref(false)
const sending = ref(false)
const error = ref('')
const copied = ref('')
const activeTab = ref('params')
const responseTab = ref('body')
const result = ref(null)
const lifetime = new AbortController()
const api = createWebuiApi({baseUrl: API_BASE, onSessionExpired: logoutWebui})
const queryUrl = computed(() => {
  const url = new URL(endpoint, window.location.origin)
  if (identity.value.trim()) url.searchParams.set(identityKey.value, identity.value.trim())
  if (ticket.value) url.searchParams.set('ticket', ticket.value)
  return url.toString()
})
const output = computed(() => result.value ? responseTab.value === 'headers' ? result.value.headers : result.value.body : '')
const fields = [
  ['u_id', '统一账号 UUID'], ['b_id', '主项目机器人用户 OpenID'],
  ['background', '当前已审核背景；未设置时为 null'], ['background.owner', 'UGC 背景所有者标识'],
  ['background.id', '当前生效的背景 UUID'], ['submission', '最近一次投稿；没有投稿时为 null'],
  ['submission.status', 'PENDING 待审核 / REVIEWED 已通过 / DENIED 未通过'],
  ['submission.width / height / size', '最近投稿的宽、高（像素）及文件大小（字节）'],
  ['submission.createdAt / reviewedAt', '提交、审核时间（Unix 毫秒），尚未审核为 0']
]
const errorCodes = [
  {code: 10001, title: '参数无效', description: '用户标识缺失、格式错误，或传入了重复、冲突及额外参数', tone: 'warning'},
  {code: 10002, title: '鉴权失败', description: 'ticket 缺失、签名无效或已过期', tone: 'danger'},
  {code: 80014, title: '用户不存在', description: '未找到与所选标识对应的统一账号', tone: 'info'},
  {code: 90001, title: '内部错误', description: '用户数据读取失败，请稍后重试', tone: 'danger'},
  {code: 90002, title: '接口未启用', description: 'connect.secret 未配置或长度不足', tone: 'muted'}
]

async function refreshTicket() {
  refreshing.value = true
  error.value = ''
  try {
    const data = await api('/connect/ticket', {method: 'POST', body: '{}', signal: lifetime.signal})
    enabled.value = data.enabled
    ticket.value = generatedTicket.value = data.ticket || ''
    expiresAt.value = data.expiresAt
  } catch (e) {
    if (e.name !== 'AbortError') error.value = e.message
  } finally {
    refreshing.value = false
  }
}

async function send() {
  if (sending.value || refreshing.value) return
  if (!identity.value.trim()) {
    error.value = '请填写用户标识';
    return
  }
  sending.value = true
  error.value = ''
  try {
    if (!ticket.value || ticket.value === generatedTicket.value && expiresAt.value <= Date.now() + 5000) await refreshTicket()
    if (!ticket.value) {
      if (!error.value) error.value = '请先配置 connect.secret';
      return
    }
    const start = performance.now()
    const response = await fetch(queryUrl.value, {
      method: 'GET',
      cache: 'no-store',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      signal: lifetime.signal
    })
    const raw = await response.text()
    let body = raw
    try {
      body = JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
    }
    result.value = {
      status: response.status,
      duration: Math.round(performance.now() - start),
      size: new TextEncoder().encode(raw).length,
      body,
      headers: [...response.headers].map(([key, value]) => `${key}: ${value}`).join('\n')
    }
  } catch (e) {
    if (e.name !== 'AbortError') error.value = e.message || '请求失败'
  } finally {
    sending.value = false
  }
}

async function copy(value, label) {
  try {
    await navigator.clipboard.writeText(value);
    copied.value = `${label}已复制`
  } catch {
    error.value = '复制失败，请手动复制'
  }
}

onMounted(refreshTicket)
onBeforeUnmount(() => lifetime.abort())
</script>

<template>
  <AppShell v-model:open="sidebarOpen">
    <main class="workspace connect-workspace debug-workspace connect-api-workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round">
              <path d="M3 6h18M3 12h18M3 18h18"/>
            </svg>
          </button>
          <h2>API接口</h2>
        </div>
      </header>
      <section class="content connect-layout debug-workbench">
        <aside class="connect-catalog debug-catalog">
          <div class="connect-catalog-heading">
            <h3>接口目录</h3><span>1</span>
            <button type="button" class="debug-catalog-toggle debug-ghost-btn" :aria-expanded="catalogOpen"
                    aria-controls="connect-catalog-list" @click="catalogOpen = !catalogOpen">
              {{ catalogOpen ? '收起' : '选择接口' }}
            </button>
          </div>
          <div id="connect-catalog-list" class="debug-catalog-list" :class="{ 'is-open': catalogOpen }">
            <label class="debug-catalog-search">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
                <circle cx="10.5" cy="10.5" r="6.5"/>
                <path d="m16 16 5 5"/>
              </svg>
              <input v-model="catalogSearch" placeholder="搜索接口" aria-label="搜索接口"/>
            </label>
            <details v-if="endpointVisible" class="debug-catalog-group" :open="true">
              <summary>用户数据<span>1</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="m9 5 7 7-7 7"/>
                </svg>
              </summary>
              <button class="connect-endpoint debug-catalog-item active" type="button" aria-pressed="true"
                      :title="endpoint" @click="catalogOpen = false">
                <span class="debug-catalog-method method-get">GET</span><span>用户背景图信息</span>
              </button>
            </details>
            <p v-else class="connect-note">没有匹配的接口</p>
          </div>
        </aside>
        <div class="connect-main">
          <section class="connect-panel debug-panel">
            <header class="connect-heading">
              <div class="connect-title"><span class="connect-title-icon"><svg width="18" height="18"
                                                                               viewBox="0 0 24 24" fill="none"
                                                                               stroke="currentColor" stroke-width="1.6"><rect
                  x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5"/><path
                  d="m3 17 5-5 4 4 4-7 5 8"/></svg></span>
                <h3>获取用户背景图信息</h3></div>
              <span class="connect-badge">查询接口</span></header>
            <form class="debug-request" @submit.prevent="send">
              <div class="debug-url-bar">
                <div class="debug-method-wrap connect-method-wrap method-get"><span class="debug-method connect-method">GET</span>
                </div>
                <input class="debug-path" :value="endpoint" aria-label="接口路径" readonly/>
                <button class="debug-send-btn" type="submit" :disabled="sending || refreshing || !enabled">
                  {{ sending ? '发送中…' : '发送请求' }}
                </button>
              </div>
              <div class="debug-tabs" role="tablist">
                <button v-for="tab in [{key:'params',label:'请求参数'},{key:'fields',label:'响应字段'}]" :key="tab.key"
                        type="button" class="debug-tab" role="tab" :aria-selected="activeTab === tab.key"
                        :class="{active:activeTab === tab.key}" @click="activeTab=tab.key">{{ tab.label }}
                </button>
              </div>
              <div v-show="activeTab === 'params'" class="connect-params debug-tab-panel">
                <div class="connect-identity-types"><label v-for="type in identityTypes" :key="type.key"><input
                    v-model="identityKey" type="radio" :value="type.key" name="identity-type"/>{{ type.key }}
                  <span>{{ type.label }}</span></label></div>
                <label class="connect-field"><span>{{ identityKey }} <small>必填</small></span><input v-model="identity"
                                                                                                      autocomplete="off"
                                                                                                      spellcheck="false"
                                                                                                      aria-label="用户标识"
                                                                                                      placeholder="填写所选类型的用户标识"/></label>
                <label class="connect-field"><span>ticket <small>必填</small></span><input v-model="ticket"
                                                                                           autocomplete="off"
                                                                                           spellcheck="false"
                                                                                           aria-label="ticket"
                                                                                           placeholder="自动获取鉴权凭证"/></label>
                <div class="connect-auth-note"><span
                    v-if="!enabled">接口未启用，请配置 connect.secret（至少 32 字节）</span><span v-else-if="expiresAt">凭证有效至 {{
                    new Date(expiresAt).toLocaleTimeString('zh-CN', {hour12: false})
                  }}</span><span v-else>正在获取凭证</span>
                  <button class="debug-ghost-btn" type="button" :disabled="refreshing || sending"
                          @click="refreshTicket">{{ refreshing ? '获取中…' : '刷新凭证' }}
                  </button>
                </div>
              </div>
              <div v-show="activeTab === 'fields'" class="connect-fields"><p class="connect-note">业务数据位于 data，code
                为 0 表示成功。background 可直接用作 UGC 生图请求的 background 参数</p>
                <table>
                  <tbody>
                  <tr v-for="[key,description] in fields" :key="key">
                    <td><code>{{ key }}</code></td>
                    <td>{{ description }}</td>
                  </tr>
                  </tbody>
                </table>
                <details class="connect-errors">
                  <summary>错误码说明
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="1.8">
                      <path d="m9 5 7 7-7 7"/>
                    </svg>
                  </summary>
                  <ul>
                    <li v-for="item in errorCodes" :key="item.code" :class="`connect-code-${item.tone}`">
                      <code class="connect-code">{{ item.code }}</code>
                      <strong>{{ item.title }}</strong><span class="connect-code-description">{{
                        item.description
                      }}</span>
                    </li>
                  </ul>
                </details>
              </div>
              <div class="connect-copy">
                <button class="debug-ghost-btn" type="button" :disabled="!ticket || !identity.trim()"
                        @click="copy(queryUrl,'请求地址')">复制请求地址
                </button>
                <button class="debug-ghost-btn" type="button" :disabled="!ticket || !identity.trim()"
                        @click="copy(`curl --get '${queryUrl}'`,'cURL')">复制 cURL
                </button>
                <span role="status">{{ copied }}</span></div>
              <p v-if="error" class="connect-error" role="alert">{{ error }}</p>
            </form>
          </section>
          <section class="connect-panel debug-panel">
            <div class="debug-response-head"><strong>响应结果</strong>
              <div v-if="result" class="debug-response-status"><span :class="{'connect-error':result.status>=400, 'is-2xx':result.status>=200 && result.status<300}"
                                                                     class="debug-status-code">{{
                  result.status
                }}</span><span class="debug-stat">{{ result.duration }} ms</span><span class="debug-stat">{{
                  result.size
                }} B</span></div>
            </div>
            <div class="debug-tabs">
              <button v-for="tab in [{key:'body',label:'JSON'},{key:'headers',label:'Headers'}]" :key="tab.key"
                      type="button" class="debug-tab" :class="{active:responseTab===tab.key}"
                      @click="responseTab=tab.key">{{ tab.label }}
              </button>
              <div class="debug-tab-actions">
                <button class="debug-ghost-btn" :disabled="!result" @click="copy(output,'响应')">复制</button>
              </div>
            </div>
            <div v-if="result" class="debug-response-body"><pre class="connect-output debug-output">{{ output }}</pre></div>
            <div v-else class="connect-empty">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-14-2 16"/>
              </svg>
              <p>发送请求后查看响应</p></div>
          </section>
        </div>
      </section>
    </main>
  </AppShell>
</template>

<style scoped src="../../shared/styles/api-workbench.css"></style>
<style scoped src="../../shared/styles/api-workbench-layout.css"></style>
<style scoped>
.connect-api-workspace .connect-field {
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
  font-size: 11px;
}

.connect-api-workspace .connect-field input {
  height: 34px;
}

.connect-api-workspace .debug-url-bar .debug-method-wrap .connect-method {
  padding: 0 12px;
  cursor: default;
}
</style>
