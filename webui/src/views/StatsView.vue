<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="fetchOverview">刷新</button>
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
          <h2>统计数据</h2>
        </div>
      </header>

      <section class="content stats-layout">
        <section class="stats-section">
          <header class="stats-section-head">
            <h3 class="stats-section-title">整体概览</h3>
            <span class="stats-range-hint">{{ rangeHint }}</span>
          </header>

          <div class="stats-toolbar">
            <label class="stats-field">
              <span class="stats-field-label">开始</span>
              <input v-model="startDate" class="stats-input" type="date" :max="endDate || undefined"/>
            </label>
            <label class="stats-field">
              <span class="stats-field-label">结束</span>
              <input v-model="endDate" class="stats-input" type="date" :min="startDate || undefined"/>
            </label>
            <button class="primary-button stats-query-btn" :disabled="loading" @click="fetchOverview">
              {{ loading ? '查询中...' : '查询' }}
            </button>
            <span class="stats-toolbar-sep" aria-hidden="true"></span>
            <div class="stats-presets">
              <button class="ghost-button" :disabled="loading" @click="applyPreset(0)">今天</button>
              <button class="ghost-button" :disabled="loading" @click="applyPreset(6)">近 7 天</button>
              <button class="ghost-button" :disabled="loading" @click="applyPreset(29)">近 30 天</button>
            </div>
          </div>

          <div v-if="loading" class="empty-state stats-state">加载中...</div>
          <div v-else-if="error" class="empty-state error stats-state">{{ error }}</div>
          <div v-else-if="!overview" class="empty-state stats-state">暂无数据</div>

          <template v-else>
            <div class="stats-metrics">
              <article class="stats-metric stats-metric--hero">
                <div class="stats-metric-label">活跃用户数量</div>
                <div class="stats-metric-value">{{ formatNumber(overview.dau) }}</div>
                <div class="stats-metric-sub">范围内去重活跃用户</div>
              </article>
              <article class="stats-metric">
                <div class="stats-metric-label">历史日均 DAU</div>
                <div class="stats-metric-value">{{ formatDecimal(overview.totalDau) }}</div>
                <div class="stats-metric-sub">全量历史按天平均</div>
              </article>
              <article class="stats-metric">
                <div class="stats-metric-label">群聊接收</div>
                <div class="stats-metric-value">{{ formatNumber(counts.groupReceived) }}</div>
                <div class="stats-metric-sub">用户发往机器人</div>
              </article>
              <article class="stats-metric">
                <div class="stats-metric-label">群聊发送</div>
                <div class="stats-metric-value">{{ formatNumber(counts.groupSent) }}</div>
                <div class="stats-metric-sub">机器人发出</div>
              </article>
              <article class="stats-metric">
                <div class="stats-metric-label">私聊接收</div>
                <div class="stats-metric-value">{{ formatNumber(counts.c2cReceived) }}</div>
                <div class="stats-metric-sub">用户发来</div>
              </article>
              <article class="stats-metric">
                <div class="stats-metric-label">私聊发送</div>
                <div class="stats-metric-value">{{ formatNumber(counts.c2cSent) }}</div>
                <div class="stats-metric-sub">机器人发出</div>
              </article>
            </div>

            <div class="stats-chart">
              <div class="stats-subhead">
                <span class="stats-subhead-title">消息量趋势</span>
                <div v-if="points.length" class="stats-legend" role="group" aria-label="切换数据系列">
                  <button v-for="s in MESSAGE_SERIES" :key="s.key" type="button" class="stats-legend-item"
                          :class="{ 'is-off': isHidden(s.key) }" :aria-pressed="String(!isHidden(s.key))"
                          @click="toggleSeries(s.key)">
                    <i class="stats-swatch" :style="isHidden(s.key) ? null : { background: s.color }"></i>{{ s.name }}
                  </button>
                </div>
              </div>

              <div v-if="seriesLoading" class="empty-state stats-state stats-chart-state">加载中...</div>
              <div v-else-if="seriesError" class="empty-state error stats-state stats-chart-state">{{ seriesError }}</div>
              <div v-else-if="!points.length" class="empty-state stats-state stats-chart-state">该时间范围内暂无记录</div>

              <div v-else ref="plotsEl" class="stats-plots">
                <svg class="stats-svg" :width="plotWidth" :height="MSG_H" :viewBox="`0 0 ${plotWidth} ${MSG_H}`"
                     role="img" aria-label="群聊 / 私聊 消息收发量按日趋势"
                     @pointermove="onPointerMove" @pointerleave="onPointerLeave">
                  <line v-for="t in msgTicks" :key="`mg-${t.v}`" class="stats-grid-line"
                        :x1="PAD.left" :x2="plotRight" :y1="t.y" :y2="t.y"/>
                  <text v-for="t in msgTicks" :key="`ml-${t.v}`" class="stats-axis-label"
                        :x="PAD.left - 8" :y="t.y + 3.5" text-anchor="end">{{ t.label }}</text>
                  <text v-for="t in xTicks" :key="`mx-${t.ts}`" class="stats-axis-label"
                        :x="t.x" :y="MSG_H - PAD.bottom + 15" :text-anchor="t.anchor">{{ t.label }}</text>

                  <line v-if="hoverPoint" class="stats-guide" :x1="hoverX" :x2="hoverX"
                        :y1="PAD.top" :y2="MSG_H - PAD.bottom"/>

                  <path v-for="s in msgLines" :key="`mp-${s.key}`" class="stats-line" :d="s.d"
                        :style="{ stroke: s.color }"/>
                  <circle v-for="d in msgDots" :key="d.id" class="stats-dot" :cx="d.x" :cy="d.y" r="2.6"
                          :style="{ fill: d.color }"/>
                  <circle v-for="d in msgHoverDots" :key="`mh-${d.key}`" class="stats-dot" :cx="hoverX" :cy="d.y"
                          r="3.6" :style="{ fill: d.color }"/>

                  <g v-for="l in endLabels" :key="`me-${l.key}`">
                    <circle :cx="plotRight + 8" :cy="l.y" r="3" :style="{ fill: l.color }"/>
                    <text class="stats-end-label" :x="plotRight + 15" :y="l.y + 3.5">{{ l.name }}</text>
                  </g>
                </svg>

                <div class="stats-plot-caption">活跃用户 DAU 数据</div>

                <svg class="stats-svg" :width="plotWidth" :height="DAU_H" :viewBox="`0 0 ${plotWidth} ${DAU_H}`"
                     role="img" aria-label="每日活跃用户按日趋势"
                     @pointermove="onPointerMove" @pointerleave="onPointerLeave">
                  <line v-for="t in dauTicks" :key="`dg-${t.v}`" class="stats-grid-line"
                        :x1="PAD.left" :x2="plotRight" :y1="t.y" :y2="t.y"/>
                  <text v-for="t in dauTicks" :key="`dl-${t.v}`" class="stats-axis-label"
                        :x="PAD.left - 8" :y="t.y + 3.5" text-anchor="end">{{ t.label }}</text>
                  <text v-for="t in xTicks" :key="`dx-${t.ts}`" class="stats-axis-label"
                        :x="t.x" :y="DAU_H - PAD.bottom + 15" :text-anchor="t.anchor">{{ t.label }}</text>

                  <line v-if="hoverPoint" class="stats-guide" :x1="hoverX" :x2="hoverX"
                        :y1="PAD.top" :y2="DAU_H - PAD.bottom"/>

                  <path class="stats-line" :d="dauPath" :style="{ stroke: DAU_COLOR }"/>
                  <circle v-for="d in dauDots" :key="d.id" class="stats-dot" :cx="d.x" :cy="d.y" r="2.6"
                          :style="{ fill: DAU_COLOR }"/>
                  <circle v-if="hoverPoint" class="stats-dot" :cx="hoverX" :cy="dauYAt(hoverPoint.dau)" r="3.6"
                          :style="{ fill: DAU_COLOR }"/>
                </svg>

                <div v-if="hoverPoint" class="stats-tooltip" :style="tooltipStyle">
                  <div class="stats-tooltip-date">{{ hoverPoint.date }}</div>
                  <div v-for="s in visibleSeries" :key="`tt-${s.key}`" class="stats-tooltip-row">
                    <i class="stats-swatch" :style="{ background: s.color }"></i>
                    <span class="stats-tooltip-name">{{ s.name }}</span>
                    <span class="stats-tooltip-value">{{ formatNumber(hoverPoint[s.key]) }}</span>
                  </div>
                  <div class="stats-tooltip-row stats-tooltip-row--dau">
                    <i class="stats-swatch" :style="{ background: DAU_COLOR }"></i>
                    <span class="stats-tooltip-name">DAU</span>
                    <span class="stats-tooltip-value">{{ formatNumber(hoverPoint.dau) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <footer class="stats-footnote">
              统计窗口 {{ overview.startTime || '不限' }} ~ {{ overview.endTime || '不限' }}
            </footer>
          </template>
        </section>

        <div class="stats-lookup-grid">
          <section class="stats-section stats-lookup">
            <header class="stats-section-head">
              <h3 class="stats-section-title">群聊统计</h3>
            </header>
            <div class="stats-lookup-form">
              <input v-model="groupOpenIdInput" class="stats-input stats-input--wide" type="text"
                     placeholder="群聊开放平台ID" @keyup.enter="fetchGroupStats"/>
              <button class="primary-button" :disabled="groupLoading || !groupOpenIdInput.trim()" @click="fetchGroupStats">
                {{ groupLoading ? '查询中...' : '查询' }}
              </button>
            </div>
            <div v-if="groupLoading" class="empty-state stats-state">加载中...</div>
            <div v-else-if="groupError" class="empty-state error stats-state">{{ groupError }}</div>
            <div v-else-if="!groupStats" class="empty-state stats-state">使用群聊开放平台ID查询</div>
            <dl v-else class="stats-detail">
              <div class="stats-detail-row">
                <dt>群聊开放平台ID</dt>
                <dd class="stats-mono">{{ groupStats.groupOpenId || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>邀请人开放平台ID</dt>
                <dd class="stats-mono">{{ groupStats.opMemberOpenId || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>真实群号</dt>
                <dd class="stats-mono">{{ groupStats.realGroupId ?? '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>接收消息</dt>
                <dd>{{ formatNumber(groupStats.receivedMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>发送消息</dt>
                <dd>{{ formatNumber(groupStats.sentMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>活跃用户数</dt>
                <dd>{{ formatNumber(groupStats.activeUsers) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>首次记录</dt>
                <dd>{{ formatTime(groupStats.firstSeenAt) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>最近记录</dt>
                <dd>{{ formatTime(groupStats.lastSeenAt) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>入群时间</dt>
                <dd>{{ formatEpoch(groupStats.timestamp) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>状态</dt>
                <dd class="stats-flags">
                  <span class="badge stats-flag" :class="groupStats.whitelist ? 'is-on' : 'is-off'">白名单</span>
                  <span class="badge stats-flag" :class="groupStats.blacklisted ? 'is-on' : 'is-off'">黑名单</span>
                  <span class="badge stats-flag" :class="groupStats.allowedActive ? 'is-on' : 'is-off'">允许主动消息</span>
                </dd>
              </div>
            </dl>
          </section>

          <section class="stats-section stats-lookup">
            <header class="stats-section-head">
              <h3 class="stats-section-title">用户统计</h3>
            </header>
            <div class="stats-lookup-form">
              <input v-model="userOpenIdInput" class="stats-input stats-input--wide" type="text"
                     placeholder="用户开放平台ID" @keyup.enter="fetchUserStats"/>
              <button class="primary-button" :disabled="userLoading || !userOpenIdInput.trim()" @click="fetchUserStats">
                {{ userLoading ? '查询中...' : '查询' }}
              </button>
            </div>
            <div v-if="userLoading" class="empty-state stats-state">加载中...</div>
            <div v-else-if="userError" class="empty-state error stats-state">{{ userError }}</div>
            <div v-else-if="!userStats" class="empty-state stats-state">使用用户开放平台ID查询</div>
            <dl v-else class="stats-detail">
              <div class="stats-detail-row">
                <dt>用户开放平台ID</dt>
                <dd class="stats-mono">{{ userStats.userOpenId || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>最近用户名</dt>
                <dd>{{ userStats.lastUsername || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>角色</dt>
                <dd>{{ userStats.role || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>权限</dt>
                <dd class="stats-flags">
                  <template v-if="userPermissions.length">
                    <span v-for="perm in userPermissions" :key="perm" class="badge stats-perm">{{ perm }}</span>
                  </template>
                  <span v-else>-</span>
                </dd>
              </div>
              <div class="stats-detail-row">
                <dt>私聊接收消息</dt>
                <dd>{{ formatNumber(userStats.c2cReceivedMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>私聊发送消息</dt>
                <dd>{{ formatNumber(userStats.c2cSentMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>群聊接收消息</dt>
                <dd>{{ formatNumber(userStats.groupReceivedMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>首次记录</dt>
                <dd>{{ formatTime(userStats.firstSeenAt) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>最近记录</dt>
                <dd>{{ formatTime(userStats.lastSeenAt) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>状态</dt>
                <dd class="stats-flags">
                  <span class="badge stats-flag" :class="userStats.isBlocked ? 'is-on' : 'is-off'">已封禁</span>
                  <span class="badge stats-flag" :class="userStats.isIgnored ? 'is-on' : 'is-off'">已忽略</span>
                  <span class="badge stats-flag" :class="userStats.c2cPush ? 'is-on' : 'is-off'">私聊推送</span>
                </dd>
              </div>
            </dl>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import {ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const PUBLIC_BASE = `${API_BASE}/public/official`

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')

const sidebarOpen = ref(false)

const loading = ref(false)
const error = ref('')
const overview = ref(null)
const counts = reactive({groupReceived: 0, groupSent: 0, c2cReceived: 0, c2cSent: 0})

const startDate = ref('')
const endDate = ref('')

const groupOpenIdInput = ref('')
const groupStats = ref(null)
const groupLoading = ref(false)
const groupError = ref('')

const userOpenIdInput = ref('')
const userStats = ref(null)
const userLoading = ref(false)
const userError = ref('')

const rangeHint = computed(() => {
  if (!startDate.value && !endDate.value) return '默认统计今日'
  return `${startDate.value || '不限'} ~ ${endDate.value || '不限'}`
})

/* ────────────────────────────────────────────────────────────────
 * 趋势图：手写内联 SVG，无任何图表库依赖
 *
 * 两张独立的图共用同一条 x 轴（时间轴）与同一个 hover 索引：
 *   上图  四条消息量曲线，量级 ~10²–10⁴
 *   下图  DAU 单曲线，量级 ~10²
 * DAU 与消息量单位不同、量级差一到两个数量级，绝不共用一条 y 轴
 * （双 y 轴会让两条曲线的交叉位置变成人为产物），因此拆成两张图，
 * 各自从 0 起算、各自取整刻度，只共享 x 轴和游标。
 * ──────────────────────────────────────────────────────────────── */

const MSG_H = 236
const DAU_H = 116
const PAD = {top: 14, right: 62, bottom: 24, left: 46}
const MAX_X_TICKS = 7
const DOT_THRESHOLD = 14

const MESSAGE_SERIES = [
  {key: 'groupReceived', name: '群聊接收', color: 'var(--series-1)'},
  {key: 'groupSent', name: '群聊发送', color: 'var(--series-2)'},
  {key: 'c2cReceived', name: '私聊接收', color: 'var(--series-3)'},
  {key: 'c2cSent', name: '私聊发送', color: 'var(--series-4)'}
]
const DAU_COLOR = 'var(--color-accent)'
const BEIJING_TIME_ZONE = 'Asia/Shanghai'
const BEIJING_TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: BEIJING_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
  hourCycle: 'h23'
})

const series = ref(null)
const seriesLoading = ref(false)
const seriesError = ref('')
const hiddenSeries = ref([])
const hoverIndex = ref(-1)
const plotsEl = ref(null)
const plotWidth = ref(720)
let resizeObserver = null

const points = computed(() => {
  const raw = series.value?.points
  if (!Array.isArray(raw)) return []
  return raw
    .map(p => ({
      date: String(p?.date ?? ''),
      ts: Date.parse(`${p?.date}T00:00:00`),
      groupReceived: toNum(p?.groupReceived),
      groupSent: toNum(p?.groupSent),
      c2cReceived: toNum(p?.c2cReceived),
      c2cSent: toNum(p?.c2cSent),
      dau: toNum(p?.dau)
    }))
    .filter(p => Number.isFinite(p.ts))
    .sort((a, b) => a.ts - b.ts)
})

const visibleSeries = computed(() => MESSAGE_SERIES.filter(s => !hiddenSeries.value.includes(s.key)))
const plotRight = computed(() => Math.max(PAD.left + 1, plotWidth.value - PAD.right))
const showDots = computed(() => points.value.length <= DOT_THRESHOLD)

/* x 轴按真实时间定位，缺失的日期自然表现为更宽的间隔，而不是被压缩掉 */
const xExtent = computed(() => {
  const list = points.value
  if (!list.length) return {min: 0, max: 0}
  return {min: list[0].ts, max: list[list.length - 1].ts}
})

function xAt(ts) {
  const {min, max} = xExtent.value
  const left = PAD.left
  const right = plotRight.value
  if (!(max > min)) return (left + right) / 2
  return left + ((ts - min) / (max - min)) * (right - left)
}

/* y 轴：0 起算，取整到 1 / 2 / 2.5 / 5 的倍数，得到 4–5 条网格线 */
function niceScale(max, targetSteps) {
  if (!(max > 0)) {
    const ticks = []
    for (let i = 0; i <= targetSteps; i++) ticks.push(i)
    return {top: targetSteps, ticks}
  }
  const rough = max / targetSteps
  const mag = Math.pow(10, Math.floor(Math.log10(rough)))
  const norm = rough / mag
  const mult = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10
  const step = mult * mag
  const top = Math.ceil(max / step) * step
  const ticks = []
  for (let i = 0; i * step <= top + step / 2; i++) ticks.push(Number((i * step).toFixed(6)))
  return {top, ticks}
}

function axisY(value, top, height) {
  const usable = height - PAD.top - PAD.bottom
  const ratio = top > 0 ? Math.min(1, Math.max(0, value / top)) : 0
  return PAD.top + (1 - ratio) * usable
}

const msgScale = computed(() => {
  let max = 0
  for (const p of points.value) {
    for (const s of visibleSeries.value) max = Math.max(max, p[s.key])
  }
  return niceScale(max, 4)
})

const dauScale = computed(() => {
  let max = 0
  for (const p of points.value) max = Math.max(max, p.dau)
  return niceScale(max, 4)
})

function msgYAt(value) {
  return axisY(value, msgScale.value.top, MSG_H)
}

function dauYAt(value) {
  return axisY(value, dauScale.value.top, DAU_H)
}

const msgTicks = computed(() => msgScale.value.ticks.map(v => ({v, y: msgYAt(v), label: abbreviate(v)})))
const dauTicks = computed(() => dauScale.value.ticks.map(v => ({v, y: dauYAt(v), label: abbreviate(v)})))

/* 每个日期标签约占 40px，按可用宽度算能放几个，再封顶 7 个 */
const maxXTicks = computed(() => {
  const usable = plotRight.value - PAD.left
  return Math.max(2, Math.min(MAX_X_TICKS, Math.floor(usable / 68)))
})

/* x 轴刻度稀释：无论区间多长都只取有限几个，索引均匀取整，首尾必取 */
const xTicks = computed(() => {
  const list = points.value
  if (!list.length) return []
  if (list.length === 1) {
    return [{ts: list[0].ts, x: xAt(list[0].ts), label: shortDate(list[0].date), anchor: 'middle'}]
  }
  const count = Math.min(list.length, maxXTicks.value)
  const seen = new Set()
  const ticks = []
  for (let i = 0; i < count; i++) {
    const idx = Math.round((i * (list.length - 1)) / (count - 1))
    if (seen.has(idx)) continue
    seen.add(idx)
    const p = list[idx]
    ticks.push({
      ts: p.ts,
      x: xAt(p.ts),
      label: shortDate(p.date),
      anchor: idx === 0 ? 'start' : idx === list.length - 1 ? 'end' : 'middle'
    })
  }
  return ticks
})

function buildPath(key, yFn) {
  return points.value
    .map((p, i) => `${i ? 'L' : 'M'}${xAt(p.ts).toFixed(2)} ${yFn(p[key]).toFixed(2)}`)
    .join(' ')
}

const msgLines = computed(() => visibleSeries.value.map(s => ({...s, d: buildPath(s.key, msgYAt)})))
const dauPath = computed(() => buildPath('dau', dauYAt))

/* 点标记只在区间较短时出现，90 天的区间不该变成一片碎点 */
const msgDots = computed(() => {
  if (!showDots.value) return []
  const out = []
  for (const s of visibleSeries.value) {
    for (const p of points.value) {
      out.push({id: `${s.key}-${p.ts}`, x: xAt(p.ts), y: msgYAt(p[s.key]), color: s.color})
    }
  }
  return out
})

const dauDots = computed(() => {
  if (!showDots.value) return []
  return points.value.map(p => ({id: `dau-${p.ts}`, x: xAt(p.ts), y: dauYAt(p.dau)}))
})

/* 线端直接标注：既是可读性兜底（部分系列色对白底对比度不足 3:1），
   也让图例之外多一条「颜色以外」的识别通道。重叠时按最小间距推开。 */
const endLabels = computed(() => {
  const list = points.value
  if (!list.length) return []
  const last = list[list.length - 1]
  const items = visibleSeries.value
    .map(s => ({key: s.key, name: s.name, color: s.color, y: msgYAt(last[s.key])}))
    .sort((a, b) => a.y - b.y)
  const gap = 13
  for (let i = 1; i < items.length; i++) {
    if (items[i].y - items[i - 1].y < gap) items[i].y = items[i - 1].y + gap
  }
  const overflow = items.length ? items[items.length - 1].y - (MSG_H - PAD.bottom) : 0
  if (overflow > 0) for (const item of items) item.y -= overflow
  for (const item of items) item.y = Math.max(PAD.top, item.y)
  return items
})

const hoverPoint = computed(() => points.value[hoverIndex.value] || null)
const hoverX = computed(() => (hoverPoint.value ? xAt(hoverPoint.value.ts) : 0))

const msgHoverDots = computed(() => {
  const p = hoverPoint.value
  if (!p) return []
  return visibleSeries.value.map(s => ({key: s.key, y: msgYAt(p[s.key]), color: s.color}))
})

const tooltipStyle = computed(() => {
  const nearRight = hoverX.value > plotWidth.value - 150
  return {
    left: `${hoverX.value}px`,
    transform: nearRight ? 'translateX(calc(-100% - 10px))' : 'translateX(10px)'
  }
})

function isHidden(key) {
  return hiddenSeries.value.includes(key)
}

function toggleSeries(key) {
  if (isHidden(key)) {
    hiddenSeries.value = hiddenSeries.value.filter(k => k !== key)
  } else if (visibleSeries.value.length > 1) {
    hiddenSeries.value = [...hiddenSeries.value, key]
  }
}

function onPointerMove(event) {
  const list = points.value
  if (!list.length) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width) return
  const x = ((event.clientX - rect.left) / rect.width) * plotWidth.value
  let best = 0
  let bestDist = Infinity
  for (let i = 0; i < list.length; i++) {
    const dist = Math.abs(xAt(list[i].ts) - x)
    if (dist < bestDist) {
      bestDist = dist
      best = i
    }
  }
  hoverIndex.value = best
}

function onPointerLeave() {
  hoverIndex.value = -1
}

function measurePlot() {
  const width = plotsEl.value?.clientWidth
  if (width) plotWidth.value = width
}

function toNum(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function shortDate(date) {
  return date.length >= 10 ? date.slice(5) : date
}

function abbreviate(value) {
  const num = Number(value) || 0
  if (Math.abs(num) >= 1000) {
    const k = num / 1000
    return `${Math.abs(k) >= 10 ? Math.round(k) : Number(k.toFixed(1))}k`
  }
  return String(Math.round(num))
}

const userPermissions = computed(() => {
  const perms = userStats.value?.permissions
  if (!perms) return []
  return Array.isArray(perms) ? perms : Object.values(perms)
})

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
    logout();
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
    logout();
    throw new Error('未授权')
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

async function publicApi(path, options) {
  const res = await fetch(`${PUBLIC_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout();
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
    logout();
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

function rangeQuery(extra) {
  const params = new URLSearchParams()
  if (startDate.value) params.set('start', startDate.value)
  if (endDate.value) params.set('end', endDate.value)
  if (extra) {
    for (const [key, value] of Object.entries(extra)) {
      if (value) params.set(key, value)
    }
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

function seriesRangeQuery() {
  if (startDate.value && endDate.value && startDate.value === endDate.value) {
    return rangeQuery({start: offsetDateValue(startDate.value, -1)})
  }
  return rangeQuery()
}

async function fetchOverview() {
  loading.value = true
  error.value = ''
  try {
    const query = rangeQuery()
    const dau = await publicApi(`/dau${query}`)
    overview.value = dau
    counts.groupReceived = dau?.groupReceiveMessages ?? 0
    counts.groupSent = dau?.groupSendMessages ?? 0
    counts.c2cReceived = dau?.c2cReceiveMessages ?? 0
    counts.c2cSent = dau?.c2cSendMessages ?? 0
  } catch (e) {
    error.value = e.message
    overview.value = null
  } finally {
    loading.value = false
  }
  fetchSeries()
}

async function fetchSeries() {
  seriesLoading.value = true
  seriesError.value = ''
  hoverIndex.value = -1
  try {
    series.value = await publicApi(`/series${seriesRangeQuery()}`)
  } catch (e) {
    seriesError.value = e.message
    series.value = null
  } finally {
    seriesLoading.value = false
    await nextTick()
    measurePlot()
  }
}

async function fetchGroupStats() {
  const value = groupOpenIdInput.value.trim()
  if (!value) return
  groupLoading.value = true
  groupError.value = ''
  try {
    groupStats.value = await publicApi(`/groups/${encodeURIComponent(value)}`)
  } catch (e) {
    groupError.value = e.message
    groupStats.value = null
  } finally {
    groupLoading.value = false
  }
}

async function fetchUserStats() {
  const value = userOpenIdInput.value.trim()
  if (!value) return
  userLoading.value = true
  userError.value = ''
  try {
    userStats.value = await publicApi(`/users/${encodeURIComponent(value)}`)
  } catch (e) {
    userError.value = e.message
    userStats.value = null
  } finally {
    userLoading.value = false
  }
}

function applyPreset(daysBack) {
  const today = new Date()
  const start = new Date(today)
  start.setDate(start.getDate() - daysBack)
  startDate.value = toDateValue(start)
  endDate.value = toDateValue(today)
  fetchOverview()
}

function toDateValue(date) {
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function offsetDateValue(value, days) {
  const parts = String(value || '').split('-').map(Number)
  if (parts.length !== 3 || parts.some(n => !Number.isFinite(n))) return value
  const date = new Date(parts[0], parts[1] - 1, parts[2])
  date.setDate(date.getDate() + days)
  return toDateValue(date)
}

function formatNumber(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return num.toLocaleString('zh-CN')
}

function formatDecimal(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return num.toFixed(2)
}

function formatEpoch(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '-'
  return formatBeijingTime(new Date(num < 1e12 ? num * 1000 : num))
}

function formatTime(value) {
  if (!value) return '-'
  const raw = String(value)
  const date = parseStatsTime(raw)
  if (!date) return raw
  return formatBeijingTime(date)
}

function parseStatsTime(raw) {
  const normalized = raw.trim().replace(' ', 'T')
  if (!normalized) return null
  if (hasExplicitTimeZone(normalized)) {
    const date = new Date(normalized)
    return Number.isNaN(date.getTime()) ? null : date
  }
  const dateOnly = normalized.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (dateOnly) {
    const [, y, m, d] = dateOnly
    return new Date(Date.UTC(Number(y), Number(m) - 1, Number(d), -8, 0, 0))
  }
  const local = normalized.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/)
  if (!local) {
    const date = new Date(normalized)
    return Number.isNaN(date.getTime()) ? null : date
  }
  const [, y, m, d, h, min, s = '0'] = local
  const utcMillis = Date.UTC(Number(y), Number(m) - 1, Number(d), Number(h) - 8, Number(min), Number(s))
  return new Date(utcMillis)
}

function hasExplicitTimeZone(value) {
  return /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
}

function formatBeijingTime(date) {
  const parts = {}
  for (const part of BEIJING_TIME_FORMATTER.formatToParts(date)) {
    if (part.type !== 'literal') parts[part.type] = part.value
  }
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
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
  const today = new Date()
  startDate.value = toDateValue(today)
  endDate.value = toDateValue(today)
  await fetchOverview()
})

/* 图表宽度按容器实测，而不是靠 viewBox 缩放 —— 这样描边宽度和字号不会被拉伸 */
watch(plotsEl, el => {
  resizeObserver?.disconnect()
  if (!el) return
  if (typeof ResizeObserver === 'undefined') {
    measurePlot()
    return
  }
  resizeObserver = new ResizeObserver(measurePlot)
  resizeObserver.observe(el)
  measurePlot()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>
