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
          <h2 class="feedback-title">群管系统</h2>
          <div class="feedback-tabs">
            <button :class="{ active: tab === 'keyword' }" @click="tab = 'keyword'">违规词撤回</button>
            <button :class="{ active: tab === 'ai' }" @click="tab = 'ai'">AI消息审查</button>
            <button :class="{ active: tab === 'join' }" @click="tab = 'join'">入群审核</button>
            <button :class="{ active: tab === 'logs' }" @click="switchToLogs">操作日志</button>
          </div>
        </div>
      </header>

      <section class="content feedback-layout">
        <section class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>群配置</strong>
            <div class="chat-head-right">
              <select v-model="groupOpenId" class="gs-group-select" @change="loadSettings">
                <option value="">选择群...</option>
                <option v-for="g in groups" :key="g.groupOpenId" :value="g.groupOpenId">{{ g.groupName || g.groupOpenId }}</option>
              </select>
              <button v-if="tab !== 'logs'" class="primary-button" :disabled="!groupOpenId || saving" @click="saveSettings">
                {{ saving ? '保存中...' : '保存设置' }}
              </button>
            </div>
          </div>

          <div class="feedback-content">
            <div v-if="!groupOpenId" class="empty-state">请先选择群（机器人身份需为管理员...）</div>
            <div v-else-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="loadError" class="empty-state error">{{ loadError }}</div>

            <!-- 违规词撤回 -->
            <div v-else-if="tab === 'keyword'" class="gm-form">
              <label class="checkbox-label">
                <input type="checkbox" v-model="settings.keywordRecall.enabled"/> 启用关键词命中撤回
              </label>

              <div class="gm-rule-list">
                <div v-if="settings.keywordRecall.rules.length === 0" class="gs-muted">暂无规则，点下方「新增规则」添加</div>
                <div v-for="(rule, i) in settings.keywordRecall.rules" :key="rule.ruleId" class="gm-rule-row">
                  <select v-model="rule.type" class="gs-input gm-rule-type">
                    <option value="KEYWORD">关键词</option>
                    <option value="LINK">链接</option>
                    <option value="MINI_PROGRAM">小程序</option>
                  </select>
                  <select v-model="rule.matchMode" class="gs-input gm-rule-mode" :disabled="rule.type !== 'KEYWORD'">
                    <option value="CONTAINS">包含</option>
                    <option value="EQUALS">完全相等</option>
                  </select>
                  <input v-model="rule.keyword" class="gs-input gm-rule-keyword" type="text"
                         :disabled="rule.type !== 'KEYWORD'" placeholder="命中词"/>
                  <input v-model="rule.remark" class="gs-input gm-rule-remark" type="text" placeholder="规则备注"/>
                  <button class="ghost-button danger gm-rule-del" title="删除规则" @click="settings.keywordRecall.rules.splice(i, 1)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                  </button>
                </div>
              </div>
              <button class="ghost-button" @click="addRule">新增规则</button>

              <div class="gm-action-block">
                <div class="gs-form-label">命中后处理</div>
                <ActionEditor v-model="settings.keywordRecall.action"/>
              </div>
            </div>

            <!-- AI 审核 -->
            <div v-else-if="tab === 'ai'" class="gm-form">
              <label class="checkbox-label">
                <input type="checkbox" v-model="settings.aiRecall.enabled"/> 启用 AI 聊天审核
              </label>
              <label class="gs-form-row">
                <span class="gs-form-label gm-prompt-label">
                  AI 审核提示词
                  <button type="button" class="ghost-button gm-default-btn" @click="settings.aiRecall.systemPrompt = DEFAULT_AI_RECALL_PROMPT">填入默认提示词</button>
                </span>
                <textarea v-model="settings.aiRecall.systemPrompt" class="gs-textarea" rows="8"
                          :placeholder="DEFAULT_AI_RECALL_PROMPT"/>
              </label>
              <div class="gm-action-block">
                <div class="gs-form-label">命中后处理</div>
                <ActionEditor v-model="settings.aiRecall.action"/>
              </div>
            </div>

            <!-- 入群审核 -->
            <div v-else-if="tab === 'join'" class="gm-form">
              <label class="gs-form-row">
                <span class="gs-form-label">审核模式</span>
                <select v-model="settings.joinReview.mode" class="gs-input gm-mode-select">
                  <option value="DISABLED">禁用</option>
                  <option value="KEYWORD">仅关键词</option>
                  <option value="AI">仅 AI</option>
                  <option value="ALL">关键词 + AI</option>
                </select>
              </label>

              <template v-if="settings.joinReview.mode === 'KEYWORD' || settings.joinReview.mode === 'ALL'">
                <div class="gs-form-label">关键词规则（对入群验证消息/问答生效，每行一个关键词）</div>
                <div class="gm-join-keyword-row">
                  <select v-model="settings.joinReview.keywordRule.matchMode" class="gs-input">
                    <option value="CONTAINS">包含</option>
                    <option value="EQUALS">完全相等</option>
                  </select>
                  <select v-model="settings.joinReview.keywordRule.onHit" class="gs-input">
                    <option value="APPROVE">命中后自动通过</option>
                    <option value="REJECT">命中后自动拒绝</option>
                  </select>
                </div>
                <textarea class="gs-textarea" rows="4" placeholder="每行一个关键词"
                          :value="(settings.joinReview.keywordRule.keywords || []).join('\n')"
                          @change="e => settings.joinReview.keywordRule.keywords = parseLines(e.target.value)"/>
              </template>

              <template v-if="settings.joinReview.mode === 'AI' || settings.joinReview.mode === 'ALL'">
                <label class="gs-form-row">
                  <span class="gs-form-label gm-prompt-label">
                    AI 审核提示词（关键词未命中时才会调用）
                    <button type="button" class="ghost-button gm-default-btn" @click="settings.joinReview.aiSystemPrompt = DEFAULT_JOIN_REVIEW_PROMPT">填入默认提示词</button>
                  </span>
                  <textarea v-model="settings.joinReview.aiSystemPrompt" class="gs-textarea" rows="6"
                            :placeholder="DEFAULT_JOIN_REVIEW_PROMPT"/>
                </label>
              </template>

              <label class="gs-form-row">
                <span class="gs-form-label">拒绝理由（自动拒绝时回传给申请人，可留空）</span>
                <input v-model="settings.joinReview.rejectReason" class="gs-input" type="text"
                       placeholder="例如：抱歉，暂不符合入群条件"/>
              </label>

              <label class="checkbox-label">
                <input type="checkbox" v-model="settings.joinReview.notifyDebugGroup"/> 通知到开发组
              </label>
            </div>

            <!-- 操作日志 -->
            <div v-else-if="tab === 'logs'">
              <div v-if="logsLoading" class="empty-state">加载中...</div>
              <div v-else-if="logs.length === 0" class="empty-state">暂无操作日志</div>
              <div v-else class="gm-log-list">
                <div v-for="row in logs" :key="row.id" class="gm-log-row">
                  <span class="feedback-tag" :class="logTagClass(row.category)">{{ logCategoryText(row.category) }}</span>
                  <span class="gm-log-action">{{ row.action }}</span>
                  <span class="gm-log-target">{{ row.targetMemberOpenId || '-' }}</span>
                  <span class="gm-log-detail">{{ row.detail }}</span>
                  <span class="feedback-time">{{ row.createdAt }}</span>
                </div>
              </div>
              <div class="feedback-pagination">
                <button class="ghost-button" :disabled="logsLoading" @click="loadLogs(logsPage + 1)">加载更多</button>
              </div>
            </div>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import ActionEditor from '../components/GroupModerationActionEditor.vue'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tab = ref('keyword')
const groups = ref([])
const groupOpenId = ref('')

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)

const settings = reactive(emptySettings())

const logs = ref([])
const logsLoading = ref(false)
const logsPage = ref(0)

const DEFAULT_AI_RECALL_PROMPT = `你是一个QQ群聊消息审核助手，负责判断群成员发送的消息是否违反群规。
需要判定为违规的情形包括但不限于：
- 包含广告、引流、加群/加好友邀请等推广内容
- 包含辱骂、人身攻击、歧视性言论
- 包含色情、低俗或其他不适宜内容
- 包含政治敏感内容
- 恶意刷屏、复读、无意义大量重复字符
如果消息内容正常，不属于以上任何一种情况，请判定为不违规。
请只根据消息文本本身判断，不要过度联想或臆测发送者意图，宁可放过也不要错杀正常发言。`

const DEFAULT_JOIN_REVIEW_PROMPT = `你是一个QQ群入群审核助手，负责根据申请人填写的验证消息或问答内容，判断是否应当同意其加入本群。
需要判定为拒绝的情形包括但不限于：
- 验证内容包含广告、引流、推广等营销信息
- 验证内容为空、无意义乱码，或明显是批量注册的机器人号特征
- 验证内容与群主题严重不符
- 验证内容包含辱骂、色情、政治敏感等违规信息
如果验证内容正常、态度诚恳，符合入群要求，请判定为通过。
请只根据申请人提供的验证内容判断，不要臆测，信息不足时倾向于判定为通过，交由管理员人工复核。`

function emptySettings() {
  return {
    keywordRecall: {enabled: false, rules: [], action: emptyAction()},
    aiRecall: {enabled: false, systemPrompt: '', action: emptyAction()},
    joinReview: {
      mode: 'DISABLED',
      keywordRule: {matchMode: 'CONTAINS', keywords: [], onHit: 'REJECT'},
      aiSystemPrompt: '',
      rejectReason: '',
      notifyDebugGroup: false
    }
  }
}

function emptyAction() {
  return {remind: true, remindMessage: '你的消息违规了哦', recall: true, mute: false, muteSeconds: 0, notifyDebugGroup: false}
}

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

async function fetchGroups() {
  try {
    const all = await api('/groups') || []
    groups.value = all.filter(g => g.memberRole === 'OWNER' || g.memberRole === 'ADMIN')
  } catch (e) {
    // 群列表失败不阻断页面
  }
}

async function loadSettings() {
  if (!groupOpenId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await api(`/group-moderation/${groupOpenId.value}`)
    Object.assign(settings, emptySettings(), data)
    if (!settings.keywordRecall.action) settings.keywordRecall.action = emptyAction()
    if (!settings.aiRecall.action) settings.aiRecall.action = emptyAction()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!groupOpenId.value) return
  saving.value = true
  try {
    await api(`/group-moderation/${groupOpenId.value}`, {method: 'PUT', body: JSON.stringify(settings)})
  } catch (e) {
    alert('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function addRule() {
  settings.keywordRecall.rules.push({
    ruleId: crypto.randomUUID(),
    type: 'KEYWORD',
    matchMode: 'CONTAINS',
    keyword: '',
    remark: ''
  })
}

function switchToLogs() {
  tab.value = 'logs'
  if (groupOpenId.value) loadLogs(1)
}

async function loadLogs(page) {
  if (!groupOpenId.value) return
  logsLoading.value = true
  try {
    const data = await api(`/group-moderation/${groupOpenId.value}/logs?page=${page}&pageSize=30`) || []
    logs.value = page === 1 ? data : logs.value.concat(data)
    logsPage.value = page
  } catch (e) {
    alert('加载日志失败: ' + e.message)
  } finally {
    logsLoading.value = false
  }
}

function parseLines(text) {
  if (!text) return []
  return text.split('\n').map(s => s.trim()).filter(Boolean)
}

function logCategoryText(category) {
  if (category === 'KEYWORD_RECALL') return '关键词撤回'
  if (category === 'AI_RECALL') return 'AI 撤回'
  if (category === 'JOIN_REVIEW') return '入群审核'
  return category
}

function logTagClass(category) {
  if (category === 'JOIN_REVIEW') return 'feedback-tag--replied'
  return 'feedback-tag--pending'
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
})
</script>
