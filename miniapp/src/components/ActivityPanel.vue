<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, shallowRef} from 'vue'
import Icon from './Icon.vue'
import {number, recordDate, rewardName, type Activity, type PrivateRequest} from '../activity'

const props = defineProps<{
  request: PrivateRequest;
}>()
const emit = defineEmits<{ ready: []; copy: [value: string] }>()
const activity = shallowRef<Activity | null>(null)
let disposed = false
onBeforeUnmount(() => {
  disposed = true;
})
const busy = reactive({activity: false})
const failed = reactive({activity: false})
const problem = (section: string) => failed.activity || Boolean(activity.value?.unavailable.includes(section))
const games = computed(() => {
  const ratio = (wins: number, plays: number) => plays ? `${Math.round(wins / plays * 1000) / 10}%` : '—'
  const definitions = [
    {id: 'minesweeper', name: '扫雷', icon: 'mine'}, {id: 'sound', name: '听声辨物', icon: 'sound'},
    {id: 'connect4', name: '四子棋', icon: 'grid'}, {id: 'roulette', name: '幸运轮盘', icon: 'wheel'},
    {id: 'rsp', name: '石头剪刀布', icon: 'scissors'}, {id: 'reaction', name: '反应力测试', icon: 'timer'},
    {id: 'fishing', name: '今日钓鱼', icon: 'gamepad'}
  ]
  return definitions.map(def => {
    const r = activity.value?.reaction
    const s = activity.value?.games?.find(row => row.game === def.id)
    const stats = s || {operations: 0, correct: 0, plays: 0, wins: 0}
    const bad = problem(def.id === 'reaction' ? 'reaction' : 'games')
    const ready = !!activity.value && !bad
    const counters = def.id === 'reaction' ? r?.metrics : s?.metrics
    const count = (key: string, fallback = 0) => counters?.[key] ?? fallback
    const metric = (label: string, key: string, fallback = 0) => ({label, value: ready ? number(count(key, fallback)) : '—'})
    let label = '胜率', value = ready ? ratio(stats.wins, stats.plays) : '—', unit = ''
    let metrics = [{label: '参与场次', value: ready ? number(stats.plays) : '—'}, {
      label: '胜场数',
      value: ready ? number(stats.wins) : '—'
    }]
    if (def.id === 'minesweeper') {
      label = '挖格次数';
      value = ready ? number(count('digs', stats.operations)) : '—';
      unit = '次';
      metrics = [metric('参与次数', 'participations'), metric('结算场次', 'plays'), metric('通关次数', 'wins'),
        metric('失败次数', 'losses'), metric('中雷次数', 'mineHits'), metric('插旗次数', 'flagsPlaced'),
        metric('拔旗次数', 'flagsRemoved'), metric('总操作次数', 'operations', stats.operations)]
    } else if (def.id === 'fishing') {
      label = '累计金粒';
      value = ready ? number(count('gold')) : '—';
      metrics = [metric('钓鱼次数', 'plays'), metric('鱼类', 'fish'), metric('杂物', 'junk'),
        metric('宝藏', 'treasure'), metric('附魔产物', 'enchanted'),
        {label: '宝藏率', value: ready ? ratio(count('treasure'), count('plays')) : '—'}]
    } else if (def.id === 'sound') {
      label = '正确率';
      value = ready ? ratio(stats.correct, stats.operations) : '—'
      metrics = [{label: '答题次数', value: ready ? number(stats.operations) : '—'}, {
        label: '正确次数',
        value: ready ? number(stats.correct) : '—'
      }, metric('答错次数', 'wrong', stats.operations - stats.correct)]
    } else if (def.id === 'reaction') {
      label = '最好用时';
      value = ready && r?.bestMs != null ? (r.bestMs / 1000).toFixed(2) : '—';
      unit = ready && r?.bestMs != null ? '秒' : ''
      metrics = [{label: '参与场次', value: ready ? number(r?.plays ?? 0) : '—'}, {
        label: '完成次数',
        value: ready ? number(r?.completed ?? 0) : '—'
      }, metric('正确点击', 'hits'), metric('失误次数', 'misses'), metric('主动放弃', 'abandoned'), metric('超时次数', 'timeouts'),
        {label: '完成率', value: ready ? ratio(r?.completed ?? 0, r?.plays ?? 0) : '—'},
        {label: '平均完成用时', value: ready && count('timedCompletions') > 0
            ? `${(count('totalCompletedMs') / count('timedCompletions') / 1000).toFixed(2)} 秒` : '—'},
        metric('耗时样本数', 'timedCompletions'),
        {label: '累计完成用时', value: ready ? `${(count('totalCompletedMs') / 1000).toFixed(2)} 秒` : '—'}]
    } else if (def.id === 'connect4') {
      metrics.push(metric('败场数', 'losses'), metric('平局数', 'draws'), metric('落子次数', 'moves'),
        metric('正常获胜', 'normalWins'), metric('对手超时获胜', 'timeoutWins'), metric('本人落子超时', 'moveTimeouts'),
        metric('超时结束场次', 'timeoutPlays'), metric('开局参与次数', 'participations'))
    } else if (def.id === 'roulette') {
      metrics[1] = {label: '幸存次数', value: ready ? number(stats.wins) : '—'}
      metrics.push(metric('淘汰次数', 'losses'), metric('手动操作', 'actions'), metric('指定自己', 'selfTargets'),
        metric('指定他人', 'otherTargets'), metric('超时自动操作', 'timeouts'), metric('开局参与次数', 'participations'))
    } else if (def.id === 'rsp') {
      metrics.push(metric('败场数', 'losses'), metric('平局数', 'draws'), metric('石头次数', 'rock'),
        metric('剪刀次数', 'scissors'), metric('布次数', 'paper'), metric('出拳参与次数', 'participations'))
    }
    return {
      ...def, bad, label, value, unit, metrics, rank: ready && def.id === 'reaction' ? r?.rank : null,
      best: ready && def.id === 'reaction' && r?.bestMs != null ? `${recordDate(r.bestDate)} · ${r.bestMisses} 次失误` : ''
    }
  })
})

async function loadActivity() {
  if (busy.activity) return
  busy.activity = true
  try {
    const result = await props.request<Activity>('activity');
    if (result) {
      activity.value = result;
      failed.activity = false
    }
  } catch {
    failed.activity = true
  } finally {
    busy.activity = false
  }
}

function reload() {
  return loadActivity()
}

defineExpose({reload})
onMounted(() => {
  void Promise.allSettled([loadActivity()]).then(() => {
    if (!disposed) emit('ready')
  })
})
</script>

<template>
  <div class="activity-panel">
    <div class="module-heading">
      <div>
        <Icon name="gamepad"/>
        <h2>小游戏</h2></div>
      <button v-if="failed.activity || busy.activity || activity?.unavailable.length" class="text-button"
              :disabled="busy.activity" @click="loadActivity">重新读取
        <Icon name="refresh" :class="{ 'refresh-spinning': busy.activity }"/>
      </button>
    </div>
    <div class="games-grid">
      <section v-for="game in games" :key="game.id" class="game-card glass" :data-game="game.id"
               :aria-label="game.name + '成绩'" :aria-busy="busy.activity">
        <header class="game-card-heading"><span class="game-emblem"><Icon :name="game.icon"/></span>
          <h3>{{ game.name }}</h3><span v-if="game.rank" class="game-rank"
                                        :title="'用时排名 ' + game.rank">排名：#{{ number(game.rank) }}</span></header>
        <div v-if="game.bad" class="game-error">暂时无法读取</div>
        <template v-else>
          <div class="game-score"><span>{{ game.label }}</span><strong>{{ game.value }}<small
              v-if="game.unit">{{ game.unit }}</small></strong></div>
          <dl v-if="game.metrics.length" class="game-metrics">
            <div v-for="metric in game.metrics" :key="metric.label">
              <dt>{{ metric.label }}</dt>
              <dd>{{ metric.value }}</dd>
            </div>
          </dl>
          <p v-if="game.best" class="game-best">{{ game.best }}</p>
        </template>
      </section>
    </div>
    <section class="settings-card glass" aria-label="用户设置" :aria-busy="busy.activity">
      <header class="module-heading">
        <div>
          <Icon name="settings"/>
          <h2>用户设置</h2></div>
      </header>
      <div v-if="problem('settings')" class="section-empty">设置暂时无法读取
        <button class="text-button" :disabled="busy.activity" @click="loadActivity">重试
          <Icon name="refresh" :class="{ 'refresh-spinning': busy.activity }"/>
        </button>
      </div>
      <div v-else-if="!activity?.settings" class="section-empty">读取中…</div>
      <template v-else>
        <dl class="settings-fields">
          <div>
            <dt>B站 UID</dt>
            <dd>{{ activity.settings.bilibiliUid || '未绑定' }}</dd>
          </div>
        </dl>
        <div class="settings-groups">
          <div class="settings-group">
            <h3 class="settings-subtitle">Hypixel 自动领奖</h3>
            <dl class="settings-fields">
              <div>
                <dt>自动领取</dt>
                <dd><span class="setting-state" :class="{ enabled: activity.settings.reward.enabled }">{{
                    activity.settings.reward.enabled ? '已开启' : '已关闭'
                  }}</span></dd>
              </div>
              <div>
                <dt>选择方式</dt>
                <dd>{{ activity.settings.reward.mode === 'priority' ? '奖励内容优先' : '稀有度优先' }}</dd>
              </div>
            </dl>
            <div v-if="activity.settings.reward.priorities.length" class="reward-priorities"><span
                v-for="priority in activity.settings.reward.priorities"
                :key="priority.item">{{ rewardName(priority.item) }}<b>{{ priority.value }}</b></span></div>
          </div>
          <div class="settings-group">
            <h3 class="settings-subtitle">分享与邀请</h3>
            <dl class="settings-fields">
              <div>
                <dt>已邀请用户</dt>
                <dd>{{ number(activity.settings.share.invitedCount) }} 人</dd>
              </div>
              <div>
                <dt>通过邀请添加</dt>
                <dd>{{ activity.settings.share.invitedBySomeone ? '是' : '否' }}</dd>
              </div>
              <div v-if="activity.settings.share.createdAt">
                <dt>链接创建日期</dt>
                <dd>{{ recordDate(activity.settings.share.createdAt) }}</dd>
              </div>
              <div class="share-url">
                <dt>分享链接</dt>
                <dd>
                  <template v-if="activity.settings.share.url"><span
                      class="share-url-text">{{ activity.settings.share.url }}</span>
                    <button class="copy-button" aria-label="复制分享链接"
                            @click="emit('copy', activity.settings.share.url)">
                      <Icon name="copy"/>
                    </button>
                  </template>
                  <span v-else>未生成</span></dd>
              </div>
            </dl>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
